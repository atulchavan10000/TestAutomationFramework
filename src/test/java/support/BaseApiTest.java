package support;

import framework.client.ApiClient;
import framework.client.ApiClientFactory;
import framework.config.FrameworkConfig;
import framework.context.TestContext;
import org.testng.ITestContext;
import org.testng.annotations.BeforeClass;

/**
 * Thin consumer-side base class. It exposes suite configuration and client
 * construction but stores no mutable test data, tokens, or created entity IDs.
 */
public abstract class BaseApiTest {
    private FrameworkConfig frameworkConfig;

    @BeforeClass(alwaysRun = true)
    public final void receiveSuiteConfiguration(ITestContext testContext) {
        Object value = testContext.getSuite().getAttribute(SuiteConfigListener.CONFIG_ATTRIBUTE);
        if (!(value instanceof FrameworkConfig config)) {
            throw new IllegalStateException("Suite configuration listener was not registered");
        }
        frameworkConfig = config;
    }

    protected final FrameworkConfig config() {
        if (frameworkConfig == null) {
            throw new IllegalStateException("Suite configuration is not available yet");
        }
        return frameworkConfig;
    }

    protected final ApiClient client(String serviceName, TestContext context) {
        return ApiClientFactory.forTest(config(), serviceName, context);
    }
}
