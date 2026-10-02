package endtoend;

import authentication.AuthApi;
import framework.client.ApiResponse;
import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import orders.OrderApi;
import orders.model.CreateOrderRequest;
import orders.model.OrderItemRequest;
import orders.model.OrderResponse;
import payments.PaymentApi;
import payments.model.PaymentRequest;
import payments.model.PaymentResponse;
import products.ProductApi;
import products.model.ProductRequest;
import products.model.ProductResponse;
import support.BaseApiTest;
import support.ScenarioData;
import users.UserApi;
import users.model.CreateUserRequest;
import users.model.CreateUserResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static support.TestSteps.step;
import static support.TestSteps.validation;

/** Ten journeys that cross authentication, catalog, ordering and payment boundaries. */
public final class CheckoutJourneyTest extends BaseApiTest {
    @DataProvider(name = "checkoutMethods")
    public Object[][] checkoutMethods() {
        return new Object[][]{
                {"CARD", 1, "5.00"}, {"UPI", 2, "10.50"}, {"NET_BANKING", 3, "1.25"},
                {"WALLET", 1, "999.99"}, {"CASH_ON_FILE", 4, "12.00"}
        };
    }

    @Test(
            groups = {"e2e", "destructive"},
            dataProvider = "checkoutMethods",
            description = "Authenticate a user, create stock, place an order, pay it, and confirm the order")
    public void completesCheckoutUsingSupportedPaymentData(String method, int quantity, String unitPrice) {
        Flow flow = startFlow("user1", "Password123!", quantity, new BigDecimal(unitPrice), method);
        try {
            validation("Payment status is SUCCESS",
                    () -> assertThat(flow.payment.status(), is("SUCCESS")));
            validation("Order status is CONFIRMED",
                    () -> assertThat(flow.confirmed.status(), is("CONFIRMED")));
            validation("Confirmed order references the created payment",
                    () -> assertThat(flow.confirmed.paymentId(), is(flow.payment.id())));
        } finally {
            flow.cleanup();
        }
    }

    @DataProvider(name = "newUsers")
    public Object[][] newUsers() { return new Object[][]{{"Journey", "One"}, {"Journey", "Two"}}; }

    @Test(
            groups = {"e2e", "destructive"},
            dataProvider = "newUsers",
            description = "Register a new user and complete an authenticated checkout journey")
    public void newlyRegisteredUserCanCompleteCheckout(String firstName, String lastName) {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        UserApi users = new UserApi(client("user-service", context));
        String username = ScenarioData.unique("journey").replace("-", "");
        ApiResponse<CreateUserResponse> created = step("Register a new checkout user",
                () -> users.createUser(new CreateUserRequest(
                        firstName, lastName, username + "@example.com")));
        Flow flow = null;
        try {
            flow = startFlow(username, "Password123!", 1, new BigDecimal("20.00"), "CARD");
            Flow completedFlow = flow;
            validation("New user's order status is CONFIRMED",
                    () -> assertThat(completedFlow.confirmed.status(), is("CONFIRMED")));
        } finally {
            if (flow != null) flow.cleanup();
            HttpResponse deletion = step("Delete the registered checkout user",
                    () -> users.deleteUser(created.body().getId()));
            validation("User cleanup returned HTTP 204",
                    () -> assertThat(deletion.statusCode(), is(204)));
        }
    }

    @Test(
            groups = {"e2e", "destructive"},
            description = "Replay a payment with the same idempotency key and receive the original payment")
    public void paymentReplayReturnsOriginalPayment() {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        String token = step("Authenticate the checkout user",
                () -> auth(context).login("user1", "Password123!").body().accessToken());
        ProductApi products = products(context);
        long productId = step("Create a product for payment replay",
                () -> createProduct(products, 2, new BigDecimal("15.00")));
        try {
            OrderResponse order = step("Create an order for the product",
                    () -> orders(context).create(new CreateOrderRequest(
                            List.of(new OrderItemRequest(productId, 1))), token).body());
            PaymentRequest request = new PaymentRequest(order.id(), order.totalAmount(), order.currency(), "CARD");
            String key = UUID.randomUUID().toString();
            PaymentResponse first = step("Submit the original payment",
                    () -> payments(context).pay(request, token, key).body());
            PaymentResponse second = step("Replay payment with the same idempotency key",
                    () -> payments(context).pay(request, token, key).body());
            validation("Payment replay returns the original payment ID",
                    () -> assertThat(second.id(), is(first.id())));
        } finally {
            HttpResponse deletion = step("Delete the payment replay product",
                    () -> products.delete(productId));
            validation("Product cleanup returned HTTP 204",
                    () -> assertThat(deletion.statusCode(), is(204)));
        }
    }

    @Test(
            groups = {"e2e", "destructive"},
            description = "Reject an order that requests more stock than the product has")
    public void insufficientStockPropagatesFromProductToOrder() {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        String token = step("Authenticate the checkout user",
                () -> auth(context).login("user1", "Password123!").body().accessToken());
        ProductApi products = products(context);
        long productId = step("Create a product with one item in stock",
                () -> createProduct(products, 1, new BigDecimal("15.00")));
        try {
            String json = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":2}]}";
            support.RawApi orderRaw = new support.RawApi(client("order-service", context));
            HttpResponse rejected = step("Request an order for two items",
                    () -> orderRaw.postJson("/api/v1/orders", json, ScenarioData.bearer(token)));
            validation("Order is rejected with HTTP 409",
                    () -> assertThat(rejected.statusCode(), is(409)));
            ProductResponse unchanged = step("Read product stock after the rejected order",
                    () -> products.get(productId).body());
            validation("Rejected order did not reserve stock",
                    () -> assertThat(unchanged.stock(), is(1)));
        } finally {
            HttpResponse deletion = step("Delete the insufficient-stock product",
                    () -> products.delete(productId));
            validation("Product cleanup returned HTTP 204",
                    () -> assertThat(deletion.statusCode(), is(204)));
        }
    }

    @Test(
            groups = {"e2e", "destructive"},
            description = "Reuse one correlation ID across authentication, ordering, and payment calls")
    public void oneCorrelationIdSpansTheCompleteJourney() {
        TestContext context = new TestContext("correlation-journey-" + UUID.randomUUID());
        String token = step("Authenticate the checkout user",
                () -> auth(context).login("user1", "Password123!").body().accessToken());
        String correlation = context.correlationId().orElseThrow();
        ProductApi products = products(context);
        long productId = step("Create a product for the correlated journey",
                () -> createProduct(products, 1, new BigDecimal("30.00")));
        try {
            OrderResponse order = step("Create an order with the shared correlation ID",
                    () -> orders(context).create(new CreateOrderRequest(
                            List.of(new OrderItemRequest(productId, 1))), token).body());
            step("Pay for the order with the shared correlation ID",
                    () -> payments(context).pay(
                            new PaymentRequest(order.id(), order.totalAmount(), order.currency(), "CARD"),
                            token, UUID.randomUUID().toString()));
            validation("Correlation ID remains unchanged for the complete journey",
                    () -> assertThat(context.correlationId().orElseThrow(), is(correlation)));
        } finally {
            HttpResponse deletion = step("Delete the correlated-journey product",
                    () -> products.delete(productId));
            validation("Product cleanup returned HTTP 204",
                    () -> assertThat(deletion.statusCode(), is(204)));
        }
    }

    private Flow startFlow(String username, String password, int quantity, BigDecimal price, String method) {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        String token = step("Authenticate user " + username,
                () -> auth(context).login(username, password).body().accessToken());
        ProductApi products = products(context);
        long productId = step("Create a product with stock for checkout",
                () -> createProduct(products, quantity, price));
        OrderResponse order = step("Create an order and reserve product stock",
                () -> orders(context).create(new CreateOrderRequest(
                        List.of(new OrderItemRequest(productId, quantity))), token).body());
        PaymentResponse payment = step("Pay for the order using " + method,
                () -> payments(context).pay(
                        new PaymentRequest(order.id(), order.totalAmount(), order.currency(), method),
                        token, UUID.randomUUID().toString()).body());
        OrderResponse confirmed = step("Read the order after payment",
                () -> orders(context).get(order.id(), token).body());
        return new Flow(products, productId, payment, confirmed);
    }

    private long createProduct(ProductApi products, int stock, BigDecimal price) {
        return products.create(new ProductRequest(ScenarioData.unique("Journey product"), "TEST",
                price, "INR", stock, 4.5, "E2E fixture")).body().id();
    }

    private AuthApi auth(TestContext context) { return new AuthApi(client("auth-service", context)); }
    private ProductApi products(TestContext context) { return new ProductApi(client("product-service", context)); }
    private OrderApi orders(TestContext context) { return new OrderApi(client("order-service", context)); }
    private PaymentApi payments(TestContext context) { return new PaymentApi(client("payment-service", context)); }

    private record Flow(ProductApi products, long productId, PaymentResponse payment, OrderResponse confirmed) {
        void cleanup() {
            HttpResponse deletion = step("Delete the checkout product fixture",
                    () -> products.delete(productId));
            validation("Product cleanup returned HTTP 204",
                    () -> assertThat(deletion.statusCode(), is(204)));
        }
    }
}
