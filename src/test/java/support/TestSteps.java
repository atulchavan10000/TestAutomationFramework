package support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Logs business-readable test steps and validations around ordinary Java code.
 *
 * Test authors name the intent because the framework cannot reliably infer that
 * "POST /orders" means "Reserve stock and create a pending order". The wrapped
 * code and its exceptions are left unchanged; this class only adds observability.
 */
public final class TestSteps {
    static final String TEST_CASE_MDC_KEY = "testCase";
    static final String STEP_MDC_KEY = "testStep";

    private static final Logger LOG = LoggerFactory.getLogger("test.steps");
    private static final ThreadLocal<Integer> NEXT_STEP = ThreadLocal.withInitial(() -> 1);

    /** Static utility; test code uses the named step and validation methods. */
    private TestSteps() {}

    /** Runs an action that returns a value and logs its start, success, or failure. */
    public static <T> T step(String description, Supplier<T> action) {
        Objects.requireNonNull(action, "Step action must not be null");
        return execute("STEP", description, action);
    }

    /** Runs an action with no return value and logs its outcome. */
    public static void step(String description, Runnable action) {
        Objects.requireNonNull(action, "Step action must not be null");
        execute("STEP", description, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Runs an assertion block. A successful block is logged as a passed
     * validation; an AssertionError is logged and rethrown to TestNG unchanged.
     */
    public static void validation(String description, Runnable assertion) {
        Objects.requireNonNull(assertion, "Validation must not be null");
        execute("VALIDATION", description, () -> {
            assertion.run();
            return null;
        });
    }

    /** Initializes numbering and MDC state for the current TestNG worker thread. */
    static void beginTest(String testCase) {
        // TestNG may reuse the same worker thread for many invocations, so each
        // invocation explicitly resets its numbering and diagnostic context.
        NEXT_STEP.set(1);
        MDC.put(TEST_CASE_MDC_KEY, testCase);
        MDC.remove(STEP_MDC_KEY);
    }

    /** Removes all invocation state before TestNG reuses the worker thread. */
    static void endTest() {
        NEXT_STEP.remove();
        MDC.remove(STEP_MDC_KEY);
        MDC.remove(TEST_CASE_MDC_KEY);
    }

    /** Shared execution template that numbers, times, logs, and rethrows wrapped work. */
    private static <T> T execute(String type, String rawDescription, Supplier<T> action) {
        String description = requireDescription(rawDescription);
        int number = NEXT_STEP.get();
        NEXT_STEP.set(number + 1);
        String displayNumber = String.format(Locale.ROOT, "%02d", number);

        String previousStep = MDC.get(STEP_MDC_KEY);
        // Preserve a surrounding step name so nested helper calls do not erase
        // the outer business context when they complete.
        MDC.put(STEP_MDC_KEY, description);
        long started = System.nanoTime();
        LOG.info("\n  [{} {} START] {}", type, displayNumber, description);
        try {
            T result = action.get();
            LOG.info("  [{} {} PASS ] {} ({} ms)\n",
                    type, displayNumber, description, elapsedMillis(started));
            return result;
        } catch (RuntimeException | AssertionError failure) {
            LOG.error("  [{} {} FAIL ] {} ({} ms) - {}: {}\n",
                    type, displayNumber, description, elapsedMillis(started),
                    failure.getClass().getSimpleName(), singleLine(failure.getMessage()));
            throw failure;
        } finally {
            if (previousStep == null) {
                MDC.remove(STEP_MDC_KEY);
            } else {
                MDC.put(STEP_MDC_KEY, previousStep);
            }
        }
    }

    /** Converts monotonic elapsed nanoseconds into display milliseconds. */
    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    /** Requires a meaningful description and sanitizes it for single-line logs. */
    private static String requireDescription(String value) {
        Objects.requireNonNull(value, "Description must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Description must not be blank");
        }
        return singleLine(value);
    }

    /** Escapes control characters so descriptions cannot disrupt log structure. */
    private static String singleLine(String value) {
        if (value == null) {
            return "<no message>";
        }
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
