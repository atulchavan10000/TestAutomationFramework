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
import static support.TestSteps.step;
import static support.TestSteps.validation;

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

        ApiResponse<LoginResponse> loginResult = step("Authenticate the checkout user",
                () -> auth.login("user1", "Password123!"));
        String token = loginResult.body().accessToken();
        validation("Authentication returns a usable access token", () -> {
            assertThat(loginResult.rawResponse().statusCode(), is(200));
            assertThat(token, notNullValue());
            assertThat(token.isBlank(), is(false));
        });

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
            ApiResponse<ProductResponse> productResult = step("Create a product for checkout",
                    () -> products.create(productRequest));
            validation("The checkout product is created",
                    () -> assertThat(productResult.rawResponse().statusCode(), is(201)));
            productId = productResult.body().id();

            long createdProductId = productId;
            ApiResponse<OrderResponse> orderResult = step("Create an order and reserve product stock",
                    () -> orders.create(new CreateOrderRequest(
                            List.of(new OrderItemRequest(createdProductId, 2))), token));
            OrderResponse pending = orderResult.body();
            validation("The order is pending payment with the expected total", () -> {
                assertThat(orderResult.rawResponse().statusCode(), is(201));
                assertThat(pending.status(), is("PENDING_PAYMENT"));
                assertThat(pending.totalAmount(), comparesEqualTo(new BigDecimal("251.00")));
            });

            ApiResponse<PaymentResponse> paymentResult = step("Pay for the pending order",
                    () -> payments.pay(
                            new PaymentRequest(pending.id(), pending.totalAmount(), pending.currency(), "CARD"),
                            token,
                            UUID.randomUUID().toString()));
            validation("The payment succeeds for the pending order", () -> {
                assertThat(paymentResult.rawResponse().statusCode(), is(201));
                assertThat(paymentResult.body().status(), is("SUCCESS"));
                assertThat(paymentResult.body().orderId(), is(pending.id()));
            });

            ApiResponse<OrderResponse> confirmedResult = step("Read the order after payment",
                    () -> orders.get(pending.id(), token));
            validation("The order is confirmed and references the payment", () -> {
                assertThat(confirmedResult.rawResponse().statusCode(), is(200));
                assertThat(confirmedResult.body().status(), is("CONFIRMED"));
                assertThat(confirmedResult.body().paymentId(), is(paymentResult.body().id()));
            });
        } finally {
            // The product is owned by this test. Orders keep a captured product ID and price.
            if (productId != 0) {
                long id = productId;
                framework.http.HttpResponse deletion = step("Delete the checkout product fixture",
                        () -> products.delete(id));
                validation("Product cleanup returns HTTP 204",
                        () -> assertThat(deletion.statusCode(), is(204)));
            }
        }
    }
}
