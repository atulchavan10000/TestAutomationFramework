package orders;

import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.ApiResponse;
import framework.client.RequestOptions;
import framework.http.HttpMethod;
import orders.model.CreateOrderRequest;
import orders.model.OrderResponse;

import java.util.Objects;

/** Consumer API object for the authenticated order lifecycle. */
public final class OrderApi {
    private static final String PATH = "/api/v1/orders";
    private final ApiClient client;

    public OrderApi(ApiClient client) {
        this.client = Objects.requireNonNull(client, "API client must not be null");
    }

    public ApiResponse<OrderResponse> create(CreateOrderRequest request, String accessToken) {
        return client.execute(new ApiRequest(HttpMethod.POST, PATH, request, bearer(accessToken)),
                OrderResponse.class);
    }

    public ApiResponse<OrderResponse> get(long orderId, String accessToken) {
        RequestOptions options = RequestOptions.builder()
                .header("Authorization", "Bearer " + requireToken(accessToken))
                .pathParam("orderId", Long.toString(orderId)).build();
        return client.execute(new ApiRequest(HttpMethod.GET, PATH + "/{orderId}", null, options),
                OrderResponse.class);
    }

    private static RequestOptions bearer(String token) {
        return RequestOptions.builder()
                .header("Authorization", "Bearer " + requireToken(token)).build();
    }

    private static String requireToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Access token must not be blank");
        }
        return token;
    }
}
