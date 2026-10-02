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
import static support.TestSteps.step;
import static support.TestSteps.validation;

/** Thirteen authentication scenarios including login and refresh-token behavior. */
public final class AuthenticationServiceTest extends BaseApiTest {
    @DataProvider(name = "validCredentials")
    public Object[][] validCredentials() {
        return new Object[][]{{"user1", "Password123!"}, {"admin", "admin123"}};
    }

    @Test(groups = {"service", "smoke"}, dataProvider = "validCredentials")
    public void logsInSeededUsers(String username, String password) {
        ApiResponse<LoginResponse> result = step("Log in seeded user " + username,
                () -> auth().login(username, password));
        validation("Login returns usable bearer and refresh tokens", () -> {
            assertThat(result.rawResponse().statusCode(), is(200));
            assertThat(result.body().accessToken(), allOf(notNullValue(), not(emptyString())));
            assertThat(result.body().refreshToken(), allOf(notNullValue(), not(emptyString())));
            assertThat(result.body().tokenType(), equalToIgnoringCase("Bearer"));
        });
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
        HttpResponse response = step("Attempt login with invalid credentials",
                () -> raw().postJson("/api/v1/auth/login", json));
        validation("Invalid credentials are rejected with HTTP 401",
                () -> assertThat(response.statusCode(), is(401)));
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
        HttpResponse response = step("Submit an invalid login payload",
                () -> raw().postJson("/api/v1/auth/login", json));
        validation("Invalid login structure is rejected with HTTP 422",
                () -> assertThat(response.statusCode(), is(422)));
    }

    @Test(groups = "service", dataProvider = "validCredentials")
    public void refreshesTokenForActiveUser(String username, String password) {
        LoginResponse login = step("Log in user " + username + " to obtain a refresh token",
                () -> auth().login(username, password).body());
        HttpResponse refreshed = step("Exchange the refresh token for an access token",
                () -> raw().postJson("/api/v1/auth/refresh",
                        "{\"refreshToken\":\"" + login.refreshToken() + "\"}"));
        validation("Refresh returns an access token and preserves the refresh token", () -> {
            assertThat(refreshed.statusCode(), is(200));
            assertThat(RawApi.body(refreshed), containsString("\"accessToken\""));
            assertThat(RawApi.body(refreshed), containsString(login.refreshToken()));
        });
    }

    @DataProvider(name = "invalidRefreshKind")
    public Object[][] invalidRefreshKind() { return new Object[][]{{"malformed"}, {"access-token"}}; }

    @Test(groups = "service", dataProvider = "invalidRefreshKind")
    public void rejectsInvalidRefreshToken(String kind) {
        String token = "access-token".equals(kind)
                ? step("Obtain an access token for the refresh rejection scenario",
                        () -> auth().login("user1", "Password123!").body().accessToken())
                : "not-a-jwt";
        HttpResponse response = step("Attempt refresh with a " + kind + " token",
                () -> raw().postJson("/api/v1/auth/refresh",
                        "{\"refreshToken\":\"" + token + "\"}"));
        validation("The invalid refresh token is rejected with HTTP 401",
                () -> assertThat(response.statusCode(), is(401)));
    }

    private AuthApi auth() {
        return new AuthApi(client("auth-service", new TestContext(UUID.randomUUID().toString())));
    }

    private RawApi raw() {
        return new RawApi(client("auth-service", new TestContext(UUID.randomUUID().toString())));
    }
}
