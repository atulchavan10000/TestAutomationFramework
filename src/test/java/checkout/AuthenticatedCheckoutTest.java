package checkout;

import authentication.AuthApi;
import authentication.model.LoginResponse;
import framework.client.ApiResponse;
import framework.context.TestContext;
import orders.OrderApi;
import orders.model.CreateOrderRequest;
import orders.model.OrderItemRequest;
import orders.model.OrderResponse;
import org.testng.annotations.Test;
import payments.PaymentApi;
import payments.model.PaymentRequest;
import payments.model.PaymentResponse;
import products.ProductApi;
import products.model.ProductRequest;
import products.model.ProductResponse;
import support.BaseApiTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** Proves the authenticated workflow across four independently configured services. */
public final class AuthenticatedCheckoutTest extends BaseApiTest {
    @Test(groups = {"service", "destructive"})
    public void authenticatedUserPaysForAndConfirmsOrder() {
        // Sharing one test-scoped context gives every call the same correlation ID.
        TestContext context = new TestContext(UUID.randomUUID().toString());
        AuthApi auth = new AuthApi(client("auth-service", context));
        ProductApi products = new ProductApi(client("product-service", context));
        OrderApi orders = new OrderApi(client("order-service", context));
        PaymentApi payments = new PaymentApi(client("payment-service", context));

        ApiResponse<LoginResponse> loginResult = auth.login("user1", "Password123!");
        assertThat(loginResult.rawResponse().statusCode(), is(200));
        String token = loginResult.body().accessToken();
        assertThat(token, notNullValue());
        assertThat(token.isBlank(), is(false));

        long productId = 0;
        try {
            ProductRequest productRequest = new ProductRequest(
                    "Checkout fixture " + UUID.randomUUID(),
                    "TEST",
                    new BigDecimal("125.50"),
                    "INR",
                    3,
                    5.0,
                    "Created and removed by one test invocation");
            ApiResponse<ProductResponse> productResult = products.create(productRequest);
            assertThat(productResult.rawResponse().statusCode(), is(201));
            productId = productResult.body().id();

            ApiResponse<OrderResponse> orderResult = orders.create(
                    new CreateOrderRequest(List.of(new OrderItemRequest(productId, 2))), token);
            assertThat(orderResult.rawResponse().statusCode(), is(201));
            OrderResponse pending = orderResult.body();
            assertThat(pending.status(), is("PENDING_PAYMENT"));
            assertThat(pending.totalAmount(), comparesEqualTo(new BigDecimal("251.00")));

            ApiResponse<PaymentResponse> paymentResult = payments.pay(
                    new PaymentRequest(pending.id(), pending.totalAmount(), pending.currency(), "CARD"),
                    token,
                    UUID.randomUUID().toString());
            assertThat(paymentResult.rawResponse().statusCode(), is(201));
            assertThat(paymentResult.body().status(), is("SUCCESS"));
            assertThat(paymentResult.body().orderId(), is(pending.id()));

            ApiResponse<OrderResponse> confirmedResult = orders.get(pending.id(), token);
            assertThat(confirmedResult.rawResponse().statusCode(), is(200));
            assertThat(confirmedResult.body().status(), is("CONFIRMED"));
            assertThat(confirmedResult.body().paymentId(), is(paymentResult.body().id()));
        } finally {
            // The product is owned by this test. Orders keep a captured product ID and price.
            if (productId != 0) {
                assertThat(products.delete(productId).statusCode(), is(204));
            }
        }
    }
}
