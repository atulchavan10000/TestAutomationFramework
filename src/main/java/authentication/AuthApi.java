package authentication;

import authentication.model.LoginRequest;
import authentication.model.LoginResponse;
import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.ApiResponse;
import framework.http.HttpMethod;

import java.util.Objects;

/** Consumer API object for authentication operations. */
public final class AuthApi {
    private final ApiClient client;

    public AuthApi(ApiClient client) {
        this.client = Objects.requireNonNull(client, "API client must not be null");
    }

    public ApiResponse<LoginResponse> login(String username, String password) {
        return client.execute(
                new ApiRequest(HttpMethod.POST, "/api/v1/auth/login",
                        new LoginRequest(username, password)),
                LoginResponse.class);
    }
}
