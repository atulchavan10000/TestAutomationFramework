package support;

import framework.config.FrameworkConfig;
import org.testng.IInvokedMethod;
import org.testng.IInvokedMethodListener;
import org.testng.ISuite;
import org.testng.ITestResult;
import org.testng.SkipException;

import java.util.Arrays;

/**
 * Applies the selected environment's destructive-test policy before a test runs.
 *
 * Tests opt into this policy with TestNG's {@code destructive} group. Keeping the
 * decision in a listener means test methods describe what they do, while one
 * suite-level component consistently decides whether that work is permitted.
 */
public final class DestructiveTestGuard implements IInvokedMethodListener {
    public static final String DESTRUCTIVE_GROUP = "destructive";

    @Override
    public void beforeInvocation(IInvokedMethod invokedMethod, ITestResult testResult) {
        // TestNG also invokes this listener for @Before... and @After... methods.
        // Only real test methods can carry the destructive-test classification.
        if (!invokedMethod.isTestMethod() || !isDestructive(invokedMethod)) {
            return;
        }

        FrameworkConfig config = suiteConfiguration(testResult.getTestContext().getSuite());
        if (!config.allowDestructiveTests()) {
            String testName = invokedMethod.getTestMethod().getRealClass().getName()
                    + "." + invokedMethod.getTestMethod().getMethodName();

            // SkipException gives CI a visible skipped result instead of making an
            // intentionally excluded production smoke test look like a test failure.
            throw new SkipException(
                    "Skipped destructive test '" + testName + "' because environment '"
                            + config.environment() + "' does not allow destructive tests");
        }
    }

    private static boolean isDestructive(IInvokedMethod invokedMethod) {
        return Arrays.asList(invokedMethod.getTestMethod().getGroups())
                .contains(DESTRUCTIVE_GROUP);
    }

    private static FrameworkConfig suiteConfiguration(ISuite suite) {
        Object value = suite.getAttribute(SuiteConfigListener.CONFIG_ATTRIBUTE);
        if (!(value instanceof FrameworkConfig config)) {
            // Missing configuration is a broken suite setup, so this must fail
            // instead of being reported as an intentionally skipped test.
            throw new IllegalStateException("Suite configuration listener was not registered");
        }
        return config;
    }
}
