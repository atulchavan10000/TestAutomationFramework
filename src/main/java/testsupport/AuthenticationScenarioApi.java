package testsupport;

import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.RequestOptions;
import framework.http.HttpMethod;
import framework.http.HttpResponse;

import java.util.Objects;

/**
 * Consumer API object demonstrating operation-level profile selection.
 * Ordinary callers choose the operation; profile names remain inside this class.
 */
public final class AuthenticationScenarioApi {
    private final ApiClient client;

    /** Binds deterministic authentication test operations to one service client. */
    public AuthenticationScenarioApi(ApiClient client) {
        this.client = Objects.requireNonNull(client, "API client must not be null");
    }

    /** Calls the endpoint whose operation policy requires the user bearer profile. */
    public HttpResponse jwtBearer() { return get("/api/v1/test/auth/jwt-bearer", "user-jwt"); }

    /** Calls the endpoint whose operation policy requires the OAuth bearer profile. */
    public HttpResponse oauthClientCredentials() { return get("/api/v1/test/auth/oauth2", "service-oauth"); }

    /** Calls the endpoint whose operation policy requires the API-key profile. */
    public HttpResponse apiKey() { return get("/api/v1/test/auth/api-key", "test-api-key"); }

    /** Calls the endpoint that requires every member of the composite profile. */
    public HttpResponse jwtAndApiKey() { return get("/api/v1/test/auth/composite", "user-jwt-and-api-key"); }

    /** Executes one raw GET with a stable operation-level authentication profile. */
    private HttpResponse get(String path, String profile) {
        return client.executeRaw(new ApiRequest(
                HttpMethod.GET, path, null, RequestOptions.empty(), profile));
    }
}
