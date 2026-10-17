package authentication;

import authentication.model.LoginRequest;
import authentication.model.LoginResponse;
import authentication.model.RefreshRequest;
import authentication.model.OAuthTokenResponse;
import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.ApiResponse;
import framework.client.RawBody;
import framework.client.RequestOptions;
import framework.http.HttpMethod;

import java.util.Objects;
import java.util.Base64;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Consumer API object for authentication operations. */
public final class AuthApi {
    private final ApiClient client;

    /** Binds authentication operations to the configured token-service client. */
    public AuthApi(ApiClient client) {
        this.client = Objects.requireNonNull(client, "API client must not be null");
    }

    /** Logs in through the consumer service's standard authentication path. */
    public ApiResponse<LoginResponse> login(String username, String password) {
        return login("/api/v1/auth/login", username, password);
    }

    /** Logs in through a provider-configured path and maps the returned token grant. */
    public ApiResponse<LoginResponse> login(String path, String username, String password) {
        return client.execute(
                new ApiRequest(HttpMethod.POST, path,
                        new LoginRequest(username, password)),
                LoginResponse.class);
    }

    /** Exchanges a refresh token through the provider-configured refresh path. */
    public ApiResponse<LoginResponse> refresh(String path, String refreshToken) {
        return client.execute(new ApiRequest(HttpMethod.POST, path, new RefreshRequest(refreshToken)),
                LoginResponse.class);
    }

    /** Sends the OAuth client-credentials grant using HTTP Basic and a form body. */
    public ApiResponse<OAuthTokenResponse> clientCredentials(
            String path, String clientId, String clientSecret, List<String> scopes) {
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String form = "grant_type=client_credentials";
        if (!scopes.isEmpty()) form += "&scope=" + encode(String.join(" ", scopes));
        RequestOptions options = RequestOptions.builder().header("Authorization", "Basic " + basic).build();
        return client.execute(new ApiRequest(HttpMethod.POST, path, RawBody.form(form), options),
                OAuthTokenResponse.class);
    }

    /** Applies form-component encoding while representing spaces as {@code %20}. */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
