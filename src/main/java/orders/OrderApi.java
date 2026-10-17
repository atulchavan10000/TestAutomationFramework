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

    public ApiResponse<OrderResponse> create(CreateOrderRequest request) {
        return create(request, RequestOptions.empty());
    }

    /** Explicit options remain available for authentication-specific and negative tests. */
    public ApiResponse<OrderResponse> create(CreateOrderRequest request, RequestOptions options) {
        return client.execute(new ApiRequest(HttpMethod.POST, PATH, request, options),
                OrderResponse.class);
    }

    public ApiResponse<OrderResponse> get(long orderId) {
        return get(orderId, RequestOptions.builder().pathParam("orderId", Long.toString(orderId)).build());
    }

    public ApiResponse<OrderResponse> get(long orderId, RequestOptions options) {
        Objects.requireNonNull(options, "Request options must not be null");
        RequestOptions complete = options.toBuilder()
                .pathParam("orderId", Long.toString(orderId)).build();
        return client.execute(new ApiRequest(HttpMethod.GET, PATH + "/{orderId}", null, complete),
                OrderResponse.class);
    }
}
