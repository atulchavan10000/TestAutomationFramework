package framework.interceptor;

import framework.context.TestContext;
import framework.http.HttpRequest;
import framework.http.HttpResponse;
import framework.logging.BodyLogFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Logs a small summary before execution, then a response or execution failure.
 * SLF4J is the logging API; Logback is the intended runtime implementation.
 * Logback configuration selects levels, destinations, and formatting separately.
 *
 * INFO contains request/response summaries. When a BodyLogFormatter is supplied,
 * DEBUG also contains safely formatted request and response bodies.
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
    private final BodyLogFormatter bodyFormatter;

    /** Uses the default correlation-header convention. */
    public LoggingInterceptor(TestContext context) {
        this(context, "X-Correlation-ID", null);
    }

    /** Receives the test context and header name, with body logging disabled. */
    public LoggingInterceptor(TestContext context, String correlationHeaderName) {
        this(context, correlationHeaderName, null);
    }

    /**
     * Receives the formatter used for request and response bodies.
     * A null formatter disables body logging while retaining INFO summaries.
     */
    public LoggingInterceptor(
            TestContext context,
            String correlationHeaderName,
            BodyLogFormatter bodyFormatter) {
        this.context = Objects.requireNonNull(context, "Test context must not be null");
        this.correlationHeaderName = Objects.requireNonNull(
                correlationHeaderName, "Correlation header name must not be null");
        this.bodyFormatter = bodyFormatter;
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

        // Formatting can parse JSON and copy body bytes, so do it only when DEBUG
        // is active. The formatter works on a copy and never changes what is sent.
        if (bodyFormatter != null && LOG.isDebugEnabled() && request.hasBody()) {
            String contentType = request.headers()
                    .firstValue("Content-Type")
                    .orElse(null);
            LOG.debug("HTTP request body: testId={}, correlationId={}, body={}",
                    testId, correlationId, bodyFormatter.format(request.body(), contentType));
        }

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

        if (bodyFormatter != null && LOG.isDebugEnabled() && response.bodyLength() > 0) {
            LOG.debug("HTTP response body: testId={}, correlationId={}, body={}",
                    testId, correlationId,
                    bodyFormatter.format(response.body(), response.contentType().orElse(null)));
        }

        return response;
    }

    /** Keeps caller-supplied IDs from inserting new lines into a summary log event. */
    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
