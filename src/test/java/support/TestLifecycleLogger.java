package support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

/** Automatically writes a clear start and end banner for every TestNG invocation. */
public final class TestLifecycleLogger implements ITestListener {
    private static final Logger LOG = LoggerFactory.getLogger("test.lifecycle");
    private static final String DIVIDER = "=".repeat(96);

    @Override
    public void onTestStart(ITestResult result) {
        String name = testCaseName(result);
        TestSteps.beginTest(name);
        String description = result.getMethod().getDescription();
        LOG.info("\n{}\nTEST CASE START: {}\nDescription    : {}\n{}\n",
                DIVIDER, name,
                description == null || description.isBlank() ? "<not provided>" : singleLine(description),
                DIVIDER);
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        finish(result, "PASSED");
    }

    @Override
    public void onTestFailure(ITestResult result) {
        finish(result, "FAILED");
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        finish(result, "SKIPPED");
    }

    private static void finish(ITestResult result, String status) {
        String name = testCaseName(result);
        long elapsedMillis = Math.max(0, result.getEndMillis() - result.getStartMillis());
        Throwable failure = result.getThrowable();
        String reason = failure == null
                ? ""
                : "\nReason         : " + failure.getClass().getSimpleName()
                        + ": " + singleLine(failure.getMessage());
        try {
            LOG.info("\n{}\nTEST CASE END  : {}\nResult         : {}\nDuration       : {} ms{}\n{}\n",
                    DIVIDER, name, status, elapsedMillis, reason, DIVIDER);
        } finally {
            // TestNG worker threads are reused, so remove this invocation's MDC
            // values before another test starts on the same thread.
            TestSteps.endTest();
        }
    }

    private static String testCaseName(ITestResult result) {
        return result.getMethod().getRealClass().getSimpleName()
                + "." + result.getMethod().getMethodName();
    }

    private static String singleLine(String value) {
        if (value == null) {
            return "<no message>";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
