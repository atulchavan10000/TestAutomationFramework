package support;

import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.RawBody;
import framework.client.RequestOptions;
import framework.http.HttpMethod;
import framework.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Test-side raw operations for negative cases that intentionally avoid DTO mapping. */
public final class RawApi {
    private final ApiClient client;

    public RawApi(ApiClient client) {
        this.client = Objects.requireNonNull(client, "API client must not be null");
    }

    public HttpResponse get(String path) {
        return get(path, RequestOptions.empty());
    }

    public HttpResponse get(String path, RequestOptions options) {
        return client.executeRaw(new ApiRequest(HttpMethod.GET, path, null, options));
    }

    public HttpResponse postJson(String path, String json) {
        return postJson(path, json, RequestOptions.empty());
    }

    public HttpResponse postJson(String path, String json, RequestOptions options) {
        return client.executeRaw(new ApiRequest(HttpMethod.POST, path, RawBody.json(json), options));
    }

    public static String body(HttpResponse response) {
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
