package orders;

import authentication.AuthApi;
import framework.client.ApiResponse;
import framework.client.RequestOptions;
import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import orders.model.CreateOrderRequest;
import orders.model.OrderItemRequest;
import orders.model.OrderResponse;
import products.ProductApi;
import products.model.ProductRequest;
import products.model.ProductResponse;
import support.BaseApiTest;
import support.RawApi;
import support.ScenarioData;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static support.TestSteps.step;
import static support.TestSteps.validation;

/** Thirteen order-service scenarios including authorization and stock reservation. */
public final class OrderServiceTest extends BaseApiTest {
    @DataProvider(name = "protectedOperations")
    public Object[][] protectedOperations() {
        return new Object[][]{{"list", "/api/v1/orders"}, {"get", "/api/v1/orders/999999999"},
                {"create", "/api/v1/orders"}};
    }

    @Test(groups = "service", dataProvider = "protectedOperations")
    public void rejectsMissingAuthentication(String operation, String path) {
        HttpResponse response = step("Call protected order operation without authentication",
                () -> callProtected(operation, path, RequestOptions.empty()));
        validation("Missing authentication is rejected with HTTP 401",
                () -> assertThat(response.statusCode(), is(401)));
    }

    @Test(groups = "service", dataProvider = "protectedOperations")
    public void rejectsInvalidAuthentication(String operation, String path) {
        HttpResponse response = step("Call protected order operation with an invalid token",
                () -> callProtected(operation, path, ScenarioData.bearer("invalid.jwt.token")));
        validation("Invalid authentication is rejected with HTTP 401",
                () -> assertThat(response.statusCode(), is(401)));
    }

    @DataProvider(name = "invalidOrders")
    public Object[][] invalidOrders() {
        return new Object[][]{
                {"{}"}, {"{\"items\":[]}"},
                {"{\"items\":[{\"productId\":1,\"quantity\":0}]}"},
                {"{\"items\":[{\"quantity\":1}]}"}
        };
    }

    @Test(groups = "service", dataProvider = "invalidOrders")
    public void rejectsInvalidOrderPayload(String json) {
        String token = step("Authenticate the order test user", this::token);
        HttpResponse response = step("Submit an invalid order payload",
                () -> raw().postJson("/api/v1/orders", json, ScenarioData.bearer(token)));
        validation("The invalid order is rejected with HTTP 422",
                () -> assertThat(response.statusCode(), is(422)));
    }

    @DataProvider(name = "missingProductIds")
    public Object[][] missingProductIds() { return new Object[][]{{999_999_999L}}; }

    @Test(groups = "service", dataProvider = "missingProductIds")
    public void rejectsOrderForMissingProduct(long productId) {
        String json = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":1}]}";
        String token = step("Authenticate the order test user", this::token);
        HttpResponse response = step("Create an order for missing product " + productId,
                () -> raw().postJson("/api/v1/orders", json, ScenarioData.bearer(token)));
        validation("The missing product causes HTTP 404",
                () -> assertThat(response.statusCode(), is(404)));
    }

    @DataProvider(name = "quantities")
    public Object[][] quantities() { return new Object[][]{{1}, {3}}; }

    @Test(groups = {"service", "destructive"}, dataProvider = "quantities")
    public void createsPendingOrderAndReservesStock(int quantity) {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        ProductApi products = new ProductApi(client("product-service", context));
        OrderApi orders = new OrderApi(client("order-service", context));
        long productId = step("Create a product with reservable stock",
                () -> products.create(new ProductRequest(ScenarioData.unique("Order product"), "TEST",
                        new BigDecimal("25.00"), "INR", quantity + 2, 4.0, null)).body().id());
        try {
            String token = step("Authenticate the order test user", this::token);
            ApiResponse<OrderResponse> result = step("Create a pending order and reserve stock",
                    () -> orders.create(new CreateOrderRequest(
                            List.of(new OrderItemRequest(productId, quantity))), token));
            validation("The order is pending payment with the expected total", () -> {
                assertThat(result.rawResponse().statusCode(), is(201));
                assertThat(result.body().status(), is("PENDING_PAYMENT"));
                assertThat(result.body().totalAmount(), comparesEqualTo(
                        new BigDecimal("25.00").multiply(BigDecimal.valueOf(quantity))));
            });
            ProductResponse remaining = step("Read product stock after reservation",
                    () -> products.get(productId).body());
            validation("The requested quantity was reserved",
                    () -> assertThat(remaining.stock(), is(2)));
        } finally {
            HttpResponse deletion = step("Delete the order product fixture",
                    () -> products.delete(productId));
            validation("Product cleanup returns HTTP 204",
                    () -> assertThat(deletion.statusCode(), is(204)));
        }
    }

    private HttpResponse callProtected(String operation, String path, RequestOptions options) {
        return "create".equals(operation)
                ? raw().postJson(path, "{\"items\":[{\"productId\":1,\"quantity\":1}]}", options)
                : raw().get(path, options);
    }

    private String token() {
        return new AuthApi(client("auth-service", new TestContext(UUID.randomUUID().toString())))
                .login("user1", "Password123!").body().accessToken();
    }

    private RawApi raw() {
        return new RawApi(client("order-service", new TestContext(UUID.randomUUID().toString())));
    }
}
