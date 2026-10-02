package testsupport;

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

/** Ten deterministic transport scenarios supplied specifically for framework consumers. */
public final class TestSupportServiceTest extends BaseApiTest {
    @DataProvider(name = "statuses")
    public Object[][] statuses() {
        return new Object[][]{{200}, {201}, {400}, {404}, {429}, {503}};
    }

    @Test(groups = "service", dataProvider = "statuses")
    public void returnsRequestedSupportedStatus(int status) {
        HttpResponse response = step("Request the deterministic HTTP " + status + " response",
                () -> raw().get("/api/v1/test/status/" + status));
        validation("The service returns HTTP " + status,
                () -> assertThat(response.statusCode(), is(status)));
    }

    @DataProvider(name = "delays")
    public Object[][] delays() { return new Object[][]{{0}, {1}, {10}}; }

    @Test(groups = "service", dataProvider = "delays")
    public void returnsAfterRequestedDelay(int milliseconds) {
        HttpResponse response = step("Request a " + milliseconds + " ms deterministic delay",
                () -> raw().get("/api/v1/test/delay/" + milliseconds));
        validation("The response confirms the requested delay", () -> {
            assertThat(response.statusCode(), is(200));
            assertThat(RawApi.body(response), containsString("\"delayMilliseconds\":" + milliseconds));
        });
    }

    @Test(groups = "service")
    public void returnsAnEmptyNoContentResponse() {
        HttpResponse response = step("Request an empty response",
                () -> raw().get("/api/v1/test/empty"));
        validation("The response is HTTP 204 with no body", () -> {
            assertThat(response.statusCode(), is(204));
            assertThat(response.bodyLength(), is(0));
        });
    }

    private RawApi raw() {
        return new RawApi(client("test-support-service", new TestContext(UUID.randomUUID().toString())));
    }
}
