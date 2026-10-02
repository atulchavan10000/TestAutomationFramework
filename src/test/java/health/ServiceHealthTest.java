package health;

import framework.context.TestContext;
import framework.http.HttpResponse;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import support.BaseApiTest;
import support.RawApi;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static support.TestSteps.step;
import static support.TestSteps.validation;

/** Seven independent health checks: gateway plus every backing microservice. */
public final class ServiceHealthTest extends BaseApiTest {
    @DataProvider(name = "services")
    public Object[][] services() {
        return new Object[][]{
                {"api-gateway", "api-gateway"},
                {"user-service", "user-service"},
                {"auth-service", "auth-service"},
                {"product-service", "product-service"},
                {"order-service", "order-service"},
                {"payment-service", "payment-service"},
                {"test-support-service", "test-support-service"}
        };
    }

    @Test(groups = {"service", "smoke"}, dataProvider = "services")
    public void serviceIsHealthy(String serviceName, String expectedIdentity) {
        RawApi api = new RawApi(client(serviceName, new TestContext(UUID.randomUUID().toString())));
        HttpResponse response = step("Read the health status of " + serviceName,
                () -> api.get("/health"));
        validation(serviceName + " reports that it is healthy", () -> {
            assertThat(response.statusCode(), is(200));
            assertThat(RawApi.body(response), containsString("\"status\":\"UP\""));
            assertThat(RawApi.body(response), containsString(expectedIdentity));
        });
    }
}
