package api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Logs a small summary before execution, then a response or execution failure.
 * SLF4J is the logging API; Logback is the intended runtime implementation.
 * Logback configuration selects levels, destinations, and formatting separately.
 *
 * Initial scope: test/correlation IDs, method, host, status, byte count, timing.
 * Detailed URL, header, and payload logging needs a separate configurable
 * formatting/redaction policy and is intentionally outside this first example.
 *
 * Example registration order:
 * CommonHeadersInterceptor -> CorrelationIdInterceptor -> LoggingInterceptor
 * Placing this after correlation means it observes the actual outgoing header.
 * Use the same configured correlation header name in both interceptors.
 *
 * Create this interceptor for one test context, as with CorrelationIdInterceptor.
 * No request, response, or builder is stored in a field.
 */
public final class LoggingInterceptor implements HttpInterceptor {

    // A shared logger routes events; it does not hold this test's request state.
    private static final Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

    private final TestContext context;
    private final String correlationHeaderName;

    /** Uses the default correlation-header convention. */
    public LoggingInterceptor(TestContext context) {
        this(context, "X-Correlation-ID");
    }

    /** Receives the test context and the header name selected by framework setup. */
    public LoggingInterceptor(TestContext context, String correlationHeaderName) {
        this.context = Objects.requireNonNull(context, "Test context must not be null");
        this.correlationHeaderName = Objects.requireNonNull(
                correlationHeaderName, "Correlation header name must not be null");
        if (correlationHeaderName.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation header name must not be blank");
        }
    }

    /** Logs one synchronous execution, returning the exact downstream response. */
    @Override
    public HttpResponse intercept(HttpRequest request, InterceptorChain chain) {
        Objects.requireNonNull(request, "HTTP request must not be null");
        Objects.requireNonNull(chain, "Interceptor chain must not be null");

        String testId = singleLine(context.testId());
        // Read the request header, not the stored context ID: a request may override
        // correlation. Logging must not generate an ID or otherwise change state.
        // If several values were deliberately supplied, this summary shows the first.
        String correlationId = singleLine(request.headers()
                .firstValue(correlationHeaderName).orElse("<absent>"));

        // {} placeholders are filled by SLF4J. Avoid string concatenation here.
        LOG.info("HTTP request: testId={}, correlationId={}, method={}, host={}",
                testId, correlationId, request.method(), request.uri().getHost());

        // nanoTime measures elapsed time independently of wall-clock adjustments.
        long started = System.nanoTime();
        HttpResponse response;
        try {
            // Delegate exactly once. This interceptor neither retries nor asserts.
            response = chain.proceed(request);
        } catch (RuntimeException failure) {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            // Log the exception type in this minimal summary. Arbitrary exception
            // messages/stack traces may contain URLs or payloads; detailed failure
            // reporting is a later policy. The caller still receives the full cause.
            LOG.warn("HTTP execution failed: testId={}, correlationId={}, elapsedMs={}, exceptionType={}",
                    testId, correlationId, elapsedMillis, failure.getClass().getName());
            throw failure; // Preserve the same exception, including its original stack.
        }

        // A 400/500 is a received response, not an execution exception or automatic
        // test failure. Tests own status expectations. The response's elapsed time
        // is measured by the transport; the failure duration above is measured here.
        LOG.info("HTTP response: testId={}, correlationId={}, status={}, bodyBytes={}, transportMs={}",
                testId, correlationId, response.statusCode(), response.bodyLength(),
                response.elapsedTime().toMillis());

        return response;
    }

    /** Keeps caller-supplied IDs from inserting new lines into a summary log event. */
    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
