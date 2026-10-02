package payments;

import authentication.AuthApi;
import framework.client.ApiResponse;
import framework.client.RequestOptions;
import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import orders.OrderApi;
import orders.model.CreateOrderRequest;
import orders.model.OrderItemRequest;
import orders.model.OrderResponse;
import payments.model.PaymentRequest;
import payments.model.PaymentResponse;
import products.ProductApi;
import products.model.ProductRequest;
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

/** Eleven payment-service scenarios including validation and idempotency. */
public final class PaymentServiceTest extends BaseApiTest {
    @DataProvider(name = "authCases")
    public Object[][] authCases() { return new Object[][]{{"get"}, {"pay"}}; }

    @Test(groups = "service", dataProvider = "authCases")
    public void rejectsMissingAuthentication(String operation) {
        HttpResponse response = step("Call protected payment operation without authentication",
                () -> callProtected(operation, RequestOptions.builder()
                        .header("Idempotency-Key", UUID.randomUUID().toString()).build()));
        validation("Missing authentication is rejected with HTTP 401",
                () -> assertThat(response.statusCode(), is(401)));
    }

    @Test(groups = "service", dataProvider = "authCases")
    public void rejectsInvalidAuthentication(String operation) {
        RequestOptions options = RequestOptions.builder()
                .header("Authorization", "Bearer invalid.jwt.token")
                .header("Idempotency-Key", UUID.randomUUID().toString()).build();
        HttpResponse response = step("Call protected payment operation with an invalid token",
                () -> callProtected(operation, options));
        validation("Invalid authentication is rejected with HTTP 401",
                () -> assertThat(response.statusCode(), is(401)));
    }

    @DataProvider(name = "invalidPayments")
    public Object[][] invalidPayments() {
        return new Object[][]{
                {"{\"orderId\":1,\"amount\":1,\"currency\":\"INR\",\"method\":\"CARD\"}", false},
                {"{\"orderId\":1,\"amount\":0,\"currency\":\"INR\",\"method\":\"CARD\"}", true},
                {"{\"orderId\":1,\"amount\":1,\"currency\":\"INR\"}", true},
                {"{\"amount\":1,\"currency\":\"INR\",\"method\":\"CARD\"}", true}
        };
    }

    @Test(groups = "service", dataProvider = "invalidPayments")
    public void rejectsInvalidPaymentPayload(String json, boolean includeIdempotencyKey) {
        String token = step("Authenticate the payment test user", this::token);
        RequestOptions.Builder options = RequestOptions.builder()
                .header("Authorization", "Bearer " + token);
        if (includeIdempotencyKey) options.header("Idempotency-Key", UUID.randomUUID().toString());
        HttpResponse response = step("Submit an invalid payment payload",
                () -> raw().postJson("/api/v1/payments", json, options.build()));
        validation("The invalid payment is rejected with HTTP 422",
                () -> assertThat(response.statusCode(), is(422)));
    }

    @DataProvider(name = "missingPaymentIds")
    public Object[][] missingPaymentIds() { return new Object[][]{{999_999_999L}}; }

    @Test(groups = "service", dataProvider = "missingPaymentIds")
    public void returnsNotFoundForUnknownPayment(long id) {
        String token = step("Authenticate the payment test user", this::token);
        HttpResponse response = step("Read unknown payment " + id,
                () -> raw().get("/api/v1/payments/" + id, ScenarioData.bearer(token)));
        validation("The unknown payment returns HTTP 404",
                () -> assertThat(response.statusCode(), is(404)));
    }

    @DataProvider(name = "paymentQuantities")
    public Object[][] paymentQuantities() { return new Object[][]{{1}, {2}}; }

    @Test(groups = {"service", "destructive"}, dataProvider = "paymentQuantities")
    public void reusesSuccessfulPaymentForSameIdempotencyKey(int quantity) {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        ProductApi products = new ProductApi(client("product-service", context));
        OrderApi orders = new OrderApi(client("order-service", context));
        PaymentApi payments = new PaymentApi(client("payment-service", context));
        String token = step("Authenticate the payment test user", this::token);
        long productId = step("Create a product for the payment scenario",
                () -> products.create(new ProductRequest(ScenarioData.unique("Payment product"), "TEST",
                        new BigDecimal("10.00"), "INR", quantity, 4.0, null)).body().id());
        try {
            OrderResponse order = step("Create an order for payment",
                    () -> orders.create(new CreateOrderRequest(
                            List.of(new OrderItemRequest(productId, quantity))), token).body());
            String key = UUID.randomUUID().toString();
            PaymentRequest request = new PaymentRequest(order.id(), order.totalAmount(), order.currency(), "CARD");
            ApiResponse<PaymentResponse> first = step("Submit the original payment",
                    () -> payments.pay(request, token, key));
            ApiResponse<PaymentResponse> repeated = step("Replay the same idempotent payment",
                    () -> payments.pay(request, token, key));
            validation("The replay returns the original successful payment", () -> {
                assertThat(first.rawResponse().statusCode(), is(201));
                assertThat(repeated.body().id(), is(first.body().id()));
            });
            OrderResponse confirmed = step("Read the order after payment",
                    () -> orders.get(order.id(), token).body());
            validation("The paid order is confirmed",
                    () -> assertThat(confirmed.status(), is("CONFIRMED")));
        } finally {
            HttpResponse deletion = step("Delete the payment product fixture",
                    () -> products.delete(productId));
            validation("Product cleanup returns HTTP 204",
                    () -> assertThat(deletion.statusCode(), is(204)));
        }
    }

    private HttpResponse callProtected(String operation, RequestOptions options) {
        if ("get".equals(operation)) return raw().get("/api/v1/payments/999999999", options);
        return raw().postJson("/api/v1/payments",
                "{\"orderId\":1,\"amount\":1,\"currency\":\"INR\",\"method\":\"CARD\"}", options);
    }

    private String token() {
        return new AuthApi(client("auth-service", new TestContext(UUID.randomUUID().toString())))
                .login("user1", "Password123!").body().accessToken();
    }

    private RawApi raw() {
        return new RawApi(client("payment-service", new TestContext(UUID.randomUUID().toString())));
    }
}
