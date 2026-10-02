package gateway;

import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.Test;
import support.BaseApiTest;
import support.RawApi;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static support.TestSteps.step;
import static support.TestSteps.validation;

/** Three gateway-specific scenarios beyond the shared health check. */
public final class GatewayServiceTest extends BaseApiTest {
    @Test(groups = {"service", "smoke"})
    public void exposesMergedOpenApiContract() {
        HttpResponse response = step("Download the gateway's merged OpenAPI contract",
                () -> raw().get("/openapi.json"));
        validation("The merged contract contains user and payment routes", () -> {
            assertThat(response.statusCode(), is(200));
            assertThat(RawApi.body(response), containsString("/api/v1/users"));
            assertThat(RawApi.body(response), containsString("/api/v1/payments"));
        });
    }

    @Test(groups = "service")
    public void routesPublicUserEndpoint() {
        HttpResponse response = step("Request the public user route through the gateway",
                () -> raw().get("/api/v1/users"));
        validation("The gateway returns the paginated user response", () -> {
            assertThat(response.statusCode(), is(200));
            assertThat(RawApi.body(response), containsString("\"pagination\""));
        });
    }

    @Test(groups = "service")
    public void rejectsPathWithNoOwningService() {
        HttpResponse response = step("Request a route with no owning service",
                () -> raw().get("/api/v1/unknown/resource"));
        validation("The gateway returns HTTP 404",
                () -> assertThat(response.statusCode(), is(404)));
    }

    private RawApi raw() {
        return new RawApi(client("api-gateway", new TestContext(UUID.randomUUID().toString())));
    }
}
