package api;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds state belonging to ONE test execution, shared by its API steps.
 *
 * Example setup (the TestNG lifecycle wiring will be implemented later):
 * TestContext context = new TestContext(UUID.randomUUID().toString());
 *
 * Pass this same context to the components executing that test's steps.
 * Create a DIFFERENT context for every other test invocation, including separate
 * DataProvider rows and whole-test retry attempts. A method name alone may not
 * uniquely identify those invocations, so setup should supply a unique test ID.
 *
 * This class does not automatically create per-test scope. TestNG setup/wiring
 * must create and pass the right instance; never put one shared context in a
 * static field. Two references to the same object still share its state.
 *
 * Initial scope: test identity and lazily generated correlation identity only.
 * Token management, arbitrary data maps, and lifecycle cleanup are not added here.
 * Retry scoping and caller-supplied correlation-ID precedence are proposals to
 * revisit when the corresponding integration/policies are implemented.
 */
public final class TestContext {

    // Identifies this test execution even if it never makes an HTTP request.
    private final String testId;

    // Initially absent. Once generated, reuse it for this test's later requests.
    // This is an INSTANCE field: a different context has a different value.
    private String correlationId;

    /** Receives the unique execution ID chosen by test lifecycle setup. */
    public TestContext(String testId) {
        this.testId = Objects.requireNonNull(testId, "Test ID must not be null");
    }

    /** Returns this test execution's identity; does not generate a correlation ID. */
    public String testId() {
        return testId;
    }

    /**
     * Inspects the current correlation ID without creating one.
     * Optional.empty() means this context has not needed one yet.
     * This supports logging/inspection without changing the context's state.
     */
    public synchronized Optional<String> correlationId() {
        return Optional.ofNullable(correlationId);
    }

    /**
     * Generates a correlation ID on first use, then returns the same value.
     * CorrelationIdInterceptor will call this when a request needs a default ID.
     *
     * synchronized makes the check-and-create operation atomic for THIS object.
     * If two threads within one test call it at the same time, one generates the
     * ID and the other sees that same ID rather than generating a competing one.
     *
     * This does NOT isolate different tests. Separate TestContext instances do
     * that. Nor does it make future fields/methods automatically thread-safe.
     */
    public synchronized String getOrCreateCorrelationId() {
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        return correlationId;
    }
}
