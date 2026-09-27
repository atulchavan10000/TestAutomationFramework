package gateway;

import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.Test;
import support.BaseApiTest;
import support.RawApi;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/** Three gateway-specific scenarios beyond the shared health check. */
public final class GatewayServiceTest extends BaseApiTest {
    @Test(groups = {"service", "smoke"})
    public void exposesMergedOpenApiContract() {
        HttpResponse response = raw().get("/openapi.json");
        assertThat(response.statusCode(), is(200));
        assertThat(RawApi.body(response), containsString("/api/v1/users"));
        assertThat(RawApi.body(response), containsString("/api/v1/payments"));
    }

    @Test(groups = "service")
    public void routesPublicUserEndpoint() {
        HttpResponse response = raw().get("/api/v1/users");
        assertThat(response.statusCode(), is(200));
        assertThat(RawApi.body(response), containsString("\"pagination\""));
    }

    @Test(groups = "service")
    public void rejectsPathWithNoOwningService() {
        assertThat(raw().get("/api/v1/unknown/resource").statusCode(), is(404));
    }

    private RawApi raw() {
        return new RawApi(client("api-gateway", new TestContext(UUID.randomUUID().toString())));
    }
}
