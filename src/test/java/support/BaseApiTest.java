package support;

import framework.authentication.AuthenticationRuntime;
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
    private AuthenticationRuntime authenticationRuntime;

    /** Retrieves the suite-owned immutable dependencies before any class tests run. */
    @BeforeClass(alwaysRun = true)
    public final void receiveSuiteConfiguration(ITestContext testContext) {
        Object value = testContext.getSuite().getAttribute(SuiteConfigListener.CONFIG_ATTRIBUTE);
        if (!(value instanceof FrameworkConfig config)) {
            throw new IllegalStateException("Suite configuration listener was not registered");
        }
        frameworkConfig = config;
        Object authentication = testContext.getSuite().getAttribute(
                SuiteConfigListener.AUTHENTICATION_ATTRIBUTE);
        if (!(authentication instanceof AuthenticationRuntime runtime)) {
            throw new IllegalStateException("Suite authentication runtime is not available");
        }
        authenticationRuntime = runtime;
    }

    /** Returns the validated configuration snapshot selected for this suite. */
    protected final FrameworkConfig config() {
        if (frameworkConfig == null) {
            throw new IllegalStateException("Suite configuration is not available yet");
        }
        return frameworkConfig;
    }

    /**
     * Creates a client for one service and one test invocation. Token providers
     * are suite-scoped, while correlation and other mutable test state remain in
     * the supplied context.
     */
    protected final ApiClient client(String serviceName, TestContext context) {
        return ApiClientFactory.forTest(config(), serviceName, context, authenticationRuntime);
    }
}
