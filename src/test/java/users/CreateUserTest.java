package users;

import framework.client.ApiResponse;
import framework.client.RawBody;
import framework.client.RequestOptions;
import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.*;
import users.model.CreateUserRequest;
import users.model.CreateUserResponse;
import java.util.UUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import support.BaseApiTest;

/**
 * Real-service test for the confirmed POST /api/v1/users endpoint.
 * Run explicitly with: gradlew.bat serviceTest
 * The TestNG suite selects an environment; ConfigLoader resolves its service values.
 * Test data is unique, and cleanup deletes only the ID returned by this test.
 */
public class CreateUserTest extends BaseApiTest {
    private static final String SERVICE_NAME = "user-service";

    @DataProvider(name = "names", parallel = true)
    public Object[][] names() { return new Object[][] {{"Alice", "Automation"}, {"Bob", "Automation"}}; }

    @Test(groups = {"service", "destructive"}, dataProvider = "names")
    public void createsAndReadsUser(String firstName, String lastName) {
        // Locals belong to this invocation. No shared test fields or global context.
        TestContext context = new TestContext(UUID.randomUUID().toString());
        UserApi users = new UserApi(
                client(SERVICE_NAME, context));
        CreateUserRequest input = new CreateUserRequest(firstName, lastName,
                "codex-" + UUID.randomUUID() + "@example.com");
        Long createdId = null;
        Throwable primaryFailure = null;
        try {
            ApiResponse<CreateUserResponse> result = users.createUser(input);
            CreateUserResponse created = result.body();
            if (created != null) createdId = created.getId(); // capture before assertions for cleanup
            assertThat(result.rawResponse().statusCode(), is(201));
            assertThat(created, notNullValue());
            assertThat(created.getId(), greaterThan(0L));
            assertThat(created.getFirstName(), is(firstName));
            assertThat(created.getLastName(), is(lastName));
            assertThat(created.getEmail(), is(input.getEmail()));
            assertThat(created.getRole(), is("USER"));
            assertThat(created.getStatus(), is("ACTIVE"));
            assertThat(created.getUsername(), is(input.getEmail().split("@")[0]));
            assertThat(created.getCreatedAt(), notNullValue());
            String correlation = context.correlationId().orElseThrow();

            ApiResponse<CreateUserResponse> fetched = users.getUser(createdId);
            assertThat(fetched.rawResponse().statusCode(), is(200));
            assertThat(fetched.body().getId(), is(createdId));
            assertThat(fetched.body().getEmail(), is(input.getEmail()));
            assertThat(context.correlationId().orElseThrow(), is(correlation));
        } catch (RuntimeException | AssertionError failure) {
            primaryFailure = failure;
            throw failure;
        } finally {
            if (createdId != null) {
                try {
                    assertThat(users.deleteUser(createdId).statusCode(), is(204));
                } catch (RuntimeException | AssertionError cleanupFailure) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                    else throw cleanupFailure;
                }
            }
        }
    }

    @Test(groups = "service")
    public void malformedPayloadReturnsValidationEvidence() {
        UserApi users = new UserApi(client(
                SERVICE_NAME, new TestContext(UUID.randomUUID().toString())));
        HttpResponse response = users.createUserRaw(RawBody.json("{"), RequestOptions.empty());
        assertThat(response.statusCode(), is(422));
        assertThat(response.bodyLength(), greaterThan(0));
    }
}
