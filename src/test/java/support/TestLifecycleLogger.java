package support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestListener;
import org.testng.ITestResult;

/** Automatically writes a clear start and end banner for every TestNG invocation. */
public final class TestLifecycleLogger implements ITestListener {
    private static final Logger LOG = LoggerFactory.getLogger("test.lifecycle");
    private static final String DIVIDER = "=".repeat(96);

    /** Initializes per-thread step state and writes the invocation start banner. */
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

    /** Writes a successful terminal result and clears invocation-scoped logging state. */
    @Override
    public void onTestSuccess(ITestResult result) {
        finish(result, "PASSED");
    }

    /** Writes a failed terminal result, including its reason, and clears logging state. */
    @Override
    public void onTestFailure(ITestResult result) {
        finish(result, "FAILED");
    }

    /** Writes a skipped terminal result and clears invocation-scoped logging state. */
    @Override
    public void onTestSkipped(ITestResult result) {
        finish(result, "SKIPPED");
    }

    /** Writes the terminal banner and always clears state from the reusable worker thread. */
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

    /** Builds a stable class-and-method identifier for logs and MDC. */
    private static String testCaseName(ITestResult result) {
        return result.getMethod().getRealClass().getSimpleName()
                + "." + result.getMethod().getMethodName();
    }

    /** Converts nullable failure text into a safe single-line diagnostic value. */
    private static String singleLine(String value) {
        if (value == null) {
            return "<no message>";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
