package users;

import framework.client.ApiResponse;
import framework.client.RequestOptions;
import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import support.BaseApiTest;
import support.RawApi;
import support.ScenarioData;
import users.model.CreateUserRequest;
import users.model.CreateUserResponse;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** Sixteen user-service scenarios covering reads, validation and owned cleanup. */
public final class UserServiceCoverageTest extends BaseApiTest {
    @DataProvider(name = "validPages")
    public Object[][] validPages() {
        return new Object[][]{{"1", "1"}, {"1", "10"}, {"999", "10"}};
    }

    @Test(groups = "service", dataProvider = "validPages")
    public void listsUsersWithValidPagination(String page, String pageSize) {
        HttpResponse response = raw().get("/api/v1/users", RequestOptions.builder()
                .queryParam("page", page).queryParam("pageSize", pageSize).build());
        assertThat(response.statusCode(), is(200));
        assertThat(RawApi.body(response), containsString("\"pagination\""));
    }

    @DataProvider(name = "invalidPages")
    public Object[][] invalidPages() {
        return new Object[][]{{"0", "10"}, {"1", "101"}, {"text", "10"}};
    }

    @Test(groups = "service", dataProvider = "invalidPages")
    public void rejectsInvalidPagination(String page, String pageSize) {
        HttpResponse response = raw().get("/api/v1/users", RequestOptions.builder()
                .queryParam("page", page).queryParam("pageSize", pageSize).build());
        assertThat(response.statusCode(), is(422));
    }

    @DataProvider(name = "missingIds")
    public Object[][] missingIds() { return new Object[][]{{0L}, {999_999_999L}}; }

    @Test(groups = "service", dataProvider = "missingIds")
    public void returnsNotFoundForUnknownUser(long id) {
        assertThat(raw().get("/api/v1/users/" + id).statusCode(), is(404));
    }

    @DataProvider(name = "invalidUsers")
    public Object[][] invalidUsers() {
        String longName = "x".repeat(101);
        return new Object[][]{
                {"{\"lastName\":\"User\",\"email\":\"missing-first@example.com\"}"},
                {"{\"firstName\":\"\",\"lastName\":\"User\",\"email\":\"blank@example.com\"}"},
                {"{\"firstName\":\"Bad\",\"lastName\":\"Email\",\"email\":\"not-an-email\"}"},
                {"{\"firstName\":\"Missing\",\"email\":\"missing-last@example.com\"}"},
                {"{\"firstName\":\"" + longName + "\",\"lastName\":\"User\",\"email\":\"long@example.com\"}"},
                {"{\"firstName\":\"Short\",\"lastName\":\"Password\",\"email\":\"short@example.com\",\"password\":\"123\"}"}
        };
    }

    @Test(groups = "service", dataProvider = "invalidUsers")
    public void rejectsInvalidUserPayload(String json) {
        assertThat(raw().postJson("/api/v1/users", json).statusCode(), is(422));
    }

    @DataProvider(name = "validUsers")
    public Object[][] validUsers() { return new Object[][]{{"Alice", "Jenkins"}, {"B", "Q"}}; }

    @Test(groups = {"service", "destructive"}, dataProvider = "validUsers")
    public void createsReadsAndDeletesUser(String firstName, String lastName) {
        UserApi users = api();
        String email = ScenarioData.unique("user").replace("-", "") + "@example.com";
        ApiResponse<CreateUserResponse> created = users.createUser(
                new CreateUserRequest(firstName, lastName, email));
        long id = created.body().getId();
        try {
            assertThat(created.rawResponse().statusCode(), is(201));
            assertThat(created.body().getEmail(), is(email));
            assertThat(users.getUser(id).body().getFirstName(), is(firstName));
        } finally {
            assertThat(users.deleteUser(id).statusCode(), is(204));
        }
    }

    private UserApi api() {
        return new UserApi(client("user-service", new TestContext(UUID.randomUUID().toString())));
    }

    private RawApi raw() {
        return new RawApi(client("user-service", new TestContext(UUID.randomUUID().toString())));
    }
}
