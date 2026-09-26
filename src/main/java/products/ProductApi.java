package products;

import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.ApiResponse;
import framework.client.RequestOptions;
import framework.http.HttpMethod;
import framework.http.HttpResponse;
import products.model.ProductRequest;
import products.model.ProductResponse;

import java.util.Objects;

/** Consumer API object for product operations. */
public final class ProductApi {
    private static final String PATH = "/api/v1/products";
    private final ApiClient client;

    public ProductApi(ApiClient client) {
        this.client = Objects.requireNonNull(client, "API client must not be null");
    }

    public ApiResponse<ProductResponse> create(ProductRequest request) {
        return client.execute(new ApiRequest(HttpMethod.POST, PATH, request), ProductResponse.class);
    }

    public ApiResponse<ProductResponse> get(long productId) {
        RequestOptions options = RequestOptions.builder()
                .pathParam("productId", Long.toString(productId)).build();
        return client.execute(new ApiRequest(HttpMethod.GET, PATH + "/{productId}", null, options),
                ProductResponse.class);
    }

    public HttpResponse delete(long productId) {
        RequestOptions options = RequestOptions.builder()
                .pathParam("productId", Long.toString(productId)).build();
        return client.executeRaw(new ApiRequest(HttpMethod.DELETE, PATH + "/{productId}", null, options));
    }
}
