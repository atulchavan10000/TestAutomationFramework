package authentication;

import authentication.model.LoginResponse;
import framework.client.ApiResponse;
import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import support.BaseApiTest;
import support.RawApi;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** Thirteen authentication scenarios including login and refresh-token behavior. */
public final class AuthenticationServiceTest extends BaseApiTest {
    @DataProvider(name = "validCredentials")
    public Object[][] validCredentials() {
        return new Object[][]{{"user1", "Password123!"}, {"admin", "admin123"}};
    }

    @Test(groups = {"service", "smoke"}, dataProvider = "validCredentials")
    public void logsInSeededUsers(String username, String password) {
        ApiResponse<LoginResponse> result = auth().login(username, password);
        assertThat(result.rawResponse().statusCode(), is(200));
        assertThat(result.body().accessToken(), allOf(notNullValue(), not(emptyString())));
        assertThat(result.body().refreshToken(), allOf(notNullValue(), not(emptyString())));
        assertThat(result.body().tokenType(), equalToIgnoringCase("Bearer"));
    }

    @DataProvider(name = "invalidCredentials")
    public Object[][] invalidCredentials() {
        return new Object[][]{
                {"user1", "wrong"}, {"admin", "wrong"},
                {"missing-user", "Password123!"}, {"", "Password123!"}
        };
    }

    @Test(groups = "service", dataProvider = "invalidCredentials")
    public void rejectsInvalidCredentials(String username, String password) {
        String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
        assertThat(raw().postJson("/api/v1/auth/login", json).statusCode(), is(401));
    }

    @DataProvider(name = "invalidLoginShapes")
    public Object[][] invalidLoginShapes() {
        return new Object[][]{
                {"{}"}, {"{\"username\":\"user1\"}"},
                {"{\"username\":null,\"password\":null}"}
        };
    }

    @Test(groups = "service", dataProvider = "invalidLoginShapes")
    public void rejectsInvalidLoginShape(String json) {
        assertThat(raw().postJson("/api/v1/auth/login", json).statusCode(), is(422));
    }

    @Test(groups = "service", dataProvider = "validCredentials")
    public void refreshesTokenForActiveUser(String username, String password) {
        LoginResponse login = auth().login(username, password).body();
        HttpResponse refreshed = raw().postJson("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + login.refreshToken() + "\"}");
        assertThat(refreshed.statusCode(), is(200));
        assertThat(RawApi.body(refreshed), containsString("\"accessToken\""));
        assertThat(RawApi.body(refreshed), containsString(login.refreshToken()));
    }

    @DataProvider(name = "invalidRefreshKind")
    public Object[][] invalidRefreshKind() { return new Object[][]{{"malformed"}, {"access-token"}}; }

    @Test(groups = "service", dataProvider = "invalidRefreshKind")
    public void rejectsInvalidRefreshToken(String kind) {
        String token = "access-token".equals(kind)
                ? auth().login("user1", "Password123!").body().accessToken()
                : "not-a-jwt";
        HttpResponse response = raw().postJson("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + token + "\"}");
        assertThat(response.statusCode(), is(401));
    }

    private AuthApi auth() {
        return new AuthApi(client("auth-service", new TestContext(UUID.randomUUID().toString())));
    }

    private RawApi raw() {
        return new RawApi(client("auth-service", new TestContext(UUID.randomUUID().toString())));
    }
}
