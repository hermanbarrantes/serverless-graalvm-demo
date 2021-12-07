package software.amazonaws.example.product.entrypoints;

import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.CreateTableResponse;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazonaws.example.product.model.Product;
import software.amazonaws.example.product.store.ProductStore;
import software.amazonaws.example.product.store.dynamodb.DynamoDbProductStore;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS;

@Testcontainers
@TestInstance(PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ApiGatewayGetProductRequestHandlerTest {

    @Container
    static final GenericContainer dynamoDb = new GenericContainer("amazon/dynamodb-local")
            .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb")
            .withExposedPorts(8000);
    public static final String TABLE_NAME = "Products";
    private static ApiGatewayGetProductRequestHandler handler;
    private static ProductStore dynamoDbProductStore;
    private static DynamoDbClient client;

    @BeforeAll
    public static void init() {
        var endpointUrl = String.format("http://localhost:%d", dynamoDb.getFirstMappedPort());
        client = DynamoDbClient.builder()
                .endpointOverride(URI.create(endpointUrl))
                .build();
        dynamoDbProductStore = new DynamoDbProductStore(client, TABLE_NAME);
        handler = new ApiGatewayGetProductRequestHandler(dynamoDbProductStore);
    }

    @Test
    @Order(1)
    void createTable() {
        CreateTableResponse response = client.createTable(CreateTableRequest.builder()
                .tableName(TABLE_NAME)
                .attributeDefinitions(AttributeDefinition.builder()
                        .attributeName("PK")
                        .attributeType(ScalarAttributeType.S)
                        .build())
                .keySchema(KeySchemaElement.builder()
                        .attributeName("PK")
                        .keyType(KeyType.HASH)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());
        assertTrue(response.sdkHttpResponse().isSuccessful());
    }

    @Test
    @Order(2)
    public void testGetForExistingProduct() throws JSONException {
        dynamoDbProductStore.putProduct(new Product("3d22f23b-1e74-4291-a6e9-4ab53c15cd77", "Indigo Hats", new BigDecimal("13.34")));
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("id", "3d22f23b-1e74-4291-a6e9-4ab53c15cd77"))
                .build();
        APIGatewayV2HTTPResponse response = handler.handleRequest(event, new TestContext());

        assertEquals(200, response.getStatusCode());
        JSONAssert.assertEquals("""
                {
                "id": "3d22f23b-1e74-4291-a6e9-4ab53c15cd77",
                "name": "Indigo Hats",
                "price": 13.34
                }
                """, response.getBody(), JSONCompareMode.STRICT);
    }

    @Test
    @Order(3)
    public void testGetForNonExistingProduct() throws JSONException {
        APIGatewayV2HTTPEvent event = APIGatewayV2HTTPEvent.builder()
                .withPathParameters(Map.of("id", UUID.randomUUID().toString()))
                .build();
        APIGatewayV2HTTPResponse response = handler.handleRequest(event, new TestContext());

        assertEquals(404, response.getStatusCode());
        JSONAssert.assertEquals("""
                {
                "message": "Product not found"
                }
                """, response.getBody(), JSONCompareMode.STRICT);
    }
}