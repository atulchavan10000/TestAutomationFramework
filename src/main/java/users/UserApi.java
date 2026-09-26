package users;
import framework.client.ApiClient;
import framework.client.ApiRequest;
import framework.client.ApiResponse;
import framework.client.RawBody;
import framework.client.RequestOptions;
import framework.http.HttpMethod;
import framework.http.HttpResponse;
import users.model.CreateUserRequest;
import users.model.CreateUserResponse;
import java.util.Objects;
/**
 * Describes operations exposed by the API test system's user-service.
 */
public final class UserApi {
    private final ApiClient client;
    private final String createPath;
    public UserApi(ApiClient client) { this(client, "/api/v1/users"); }
    public UserApi(ApiClient client, String createPath) {
        this.client = Objects.requireNonNull(client);
        this.createPath = Objects.requireNonNull(createPath);
    }
    public ApiResponse<CreateUserResponse> createUser(CreateUserRequest body) {
        return createUser(body, RequestOptions.empty());
    }
    public ApiResponse<CreateUserResponse> createUser(CreateUserRequest body, RequestOptions options) {
        return client.execute(new ApiRequest(HttpMethod.POST, createPath, body, options), CreateUserResponse.class);
    }
    /** For malformed/wrong-type payload tests: returns HTTP evidence without success DTO mapping. */
    public HttpResponse createUserRaw(RawBody body, RequestOptions options) {
        return client.executeRaw(new ApiRequest(HttpMethod.POST, createPath, Objects.requireNonNull(body), options));
    }
    public ApiResponse<CreateUserResponse> getUser(long id) {
        return client.execute(new ApiRequest(HttpMethod.GET, createPath + "/{user_id}", null,
                RequestOptions.builder().pathParam("user_id", Long.toString(id)).build()), CreateUserResponse.class);
    }
    /** Used by test cleanup; only delete IDs owned by this test execution. */
    public HttpResponse deleteUser(long id) {
        return client.executeRaw(new ApiRequest(HttpMethod.DELETE, createPath + "/{user_id}", null,
                RequestOptions.builder().pathParam("user_id", Long.toString(id)).build()));
    }
}
