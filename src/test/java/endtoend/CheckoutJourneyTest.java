package endtoend;

import authentication.AuthApi;
import framework.client.ApiResponse;
import framework.context.TestContext;
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

/** Ten journeys that cross authentication, catalog, ordering and payment boundaries. */
public final class CheckoutJourneyTest extends BaseApiTest {
    @DataProvider(name = "checkoutMethods")
    public Object[][] checkoutMethods() {
        return new Object[][]{
                {"CARD", 1, "5.00"}, {"UPI", 2, "10.50"}, {"NET_BANKING", 3, "1.25"},
                {"WALLET", 1, "999.99"}, {"CASH_ON_FILE", 4, "12.00"}
        };
    }

    @Test(groups = {"e2e", "destructive"}, dataProvider = "checkoutMethods")
    public void completesCheckoutUsingSupportedPaymentData(String method, int quantity, String unitPrice) {
        Flow flow = startFlow("user1", "Password123!", quantity, new BigDecimal(unitPrice), method);
        try {
            assertThat(flow.payment.status(), is("SUCCESS"));
            assertThat(flow.confirmed.status(), is("CONFIRMED"));
            assertThat(flow.confirmed.paymentId(), is(flow.payment.id()));
        } finally {
            flow.cleanup();
        }
    }

    @DataProvider(name = "newUsers")
    public Object[][] newUsers() { return new Object[][]{{"Journey", "One"}, {"Journey", "Two"}}; }

    @Test(groups = {"e2e", "destructive"}, dataProvider = "newUsers")
    public void newlyRegisteredUserCanCompleteCheckout(String firstName, String lastName) {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        UserApi users = new UserApi(client("user-service", context));
        String username = ScenarioData.unique("journey").replace("-", "");
        ApiResponse<CreateUserResponse> created = users.createUser(
                new CreateUserRequest(firstName, lastName, username + "@example.com"));
        Flow flow = null;
        try {
            flow = startFlow(username, "Password123!", 1, new BigDecimal("20.00"), "CARD");
            assertThat(flow.confirmed.status(), is("CONFIRMED"));
        } finally {
            if (flow != null) flow.cleanup();
            assertThat(users.deleteUser(created.body().getId()).statusCode(), is(204));
        }
    }

    @Test(groups = {"e2e", "destructive"})
    public void paymentReplayReturnsOriginalPayment() {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        String token = auth(context).login("user1", "Password123!").body().accessToken();
        ProductApi products = products(context);
        long productId = createProduct(products, 2, new BigDecimal("15.00"));
        try {
            OrderResponse order = orders(context).create(new CreateOrderRequest(
                    List.of(new OrderItemRequest(productId, 1))), token).body();
            PaymentRequest request = new PaymentRequest(order.id(), order.totalAmount(), order.currency(), "CARD");
            String key = UUID.randomUUID().toString();
            PaymentResponse first = payments(context).pay(request, token, key).body();
            PaymentResponse second = payments(context).pay(request, token, key).body();
            assertThat(second.id(), is(first.id()));
        } finally {
            assertThat(products.delete(productId).statusCode(), is(204));
        }
    }

    @Test(groups = {"e2e", "destructive"})
    public void insufficientStockPropagatesFromProductToOrder() {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        String token = auth(context).login("user1", "Password123!").body().accessToken();
        ProductApi products = products(context);
        long productId = createProduct(products, 1, new BigDecimal("15.00"));
        try {
            String json = "{\"items\":[{\"productId\":" + productId + ",\"quantity\":2}]}";
            support.RawApi orderRaw = new support.RawApi(client("order-service", context));
            assertThat(orderRaw.postJson("/api/v1/orders", json, ScenarioData.bearer(token)).statusCode(), is(409));
            assertThat(products.get(productId).body().stock(), is(1));
        } finally {
            assertThat(products.delete(productId).statusCode(), is(204));
        }
    }

    @Test(groups = {"e2e", "destructive"})
    public void oneCorrelationIdSpansTheCompleteJourney() {
        TestContext context = new TestContext("correlation-journey-" + UUID.randomUUID());
        String token = auth(context).login("user1", "Password123!").body().accessToken();
        String correlation = context.correlationId().orElseThrow();
        ProductApi products = products(context);
        long productId = createProduct(products, 1, new BigDecimal("30.00"));
        try {
            OrderResponse order = orders(context).create(new CreateOrderRequest(
                    List.of(new OrderItemRequest(productId, 1))), token).body();
            payments(context).pay(new PaymentRequest(order.id(), order.totalAmount(), order.currency(), "CARD"),
                    token, UUID.randomUUID().toString());
            assertThat(context.correlationId().orElseThrow(), is(correlation));
        } finally {
            assertThat(products.delete(productId).statusCode(), is(204));
        }
    }

    private Flow startFlow(String username, String password, int quantity, BigDecimal price, String method) {
        TestContext context = new TestContext(UUID.randomUUID().toString());
        String token = auth(context).login(username, password).body().accessToken();
        ProductApi products = products(context);
        long productId = createProduct(products, quantity, price);
        OrderResponse order = orders(context).create(new CreateOrderRequest(
                List.of(new OrderItemRequest(productId, quantity))), token).body();
        PaymentResponse payment = payments(context).pay(
                new PaymentRequest(order.id(), order.totalAmount(), order.currency(), method),
                token, UUID.randomUUID().toString()).body();
        OrderResponse confirmed = orders(context).get(order.id(), token).body();
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
            assertThat(products.delete(productId).statusCode(), is(204));
        }
    }
}
