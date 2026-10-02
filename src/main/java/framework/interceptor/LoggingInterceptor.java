package framework.interceptor;

import framework.context.TestContext;
import framework.http.HttpRequest;
import framework.http.HttpResponse;
import framework.logging.BodyLogFormatter;
import framework.logging.SensitiveDataRedactor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Logs readable multi-line request, response, and execution-failure blocks.
 * SLF4J is the logging API; Logback is the intended runtime implementation.
 * Logback configuration selects levels, destinations, and formatting separately.
 *
 * INFO contains request/response details, redacted headers and query values.
 * When a BodyLogFormatter is supplied, each block also contains a redacted,
 * capped body. TestNG lifecycle support supplies the current test and step via MDC.
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
    private static final String DIVIDER = "-".repeat(96);

    // A shared logger routes events; it does not hold this test's request state.
    private static final Logger LOG = LoggerFactory.getLogger(LoggingInterceptor.class);

    private final TestContext context;
    private final String correlationHeaderName;
    private final BodyLogFormatter bodyFormatter;
    private final SensitiveDataRedactor redactor;

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
        this(context, correlationHeaderName, bodyFormatter, SensitiveDataRedactor.defaults());
    }

    /** Allows a consumer to replace the default sensitive-name policy. */
    public LoggingInterceptor(
            TestContext context,
            String correlationHeaderName,
            BodyLogFormatter bodyFormatter,
            SensitiveDataRedactor redactor) {
        this.context = Objects.requireNonNull(context, "Test context must not be null");
        this.correlationHeaderName = Objects.requireNonNull(
                correlationHeaderName, "Correlation header name must not be null");
        this.bodyFormatter = bodyFormatter;
        this.redactor = Objects.requireNonNull(redactor, "Redactor must not be null");
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

        String requestBody = formatRequestBody(request);
        LOG.info("\n{}\nHTTP REQUEST\n"
                        + "Test case      : {}\n"
                        + "Step           : {}\n"
                        + "Test ID        : {}\n"
                        + "Correlation ID : {}\n"
                        + "Method         : {}\n"
                        + "URI            : {}\n"
                        + "Headers        : {}\n"
                        + "Body           : {}\n{}",
                DIVIDER, currentTestCase(), currentStep(), testId, correlationId,
                request.method(), redactor.uri(request.uri()),
                redactor.headers(request.headers()), requestBody, DIVIDER);

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
            LOG.warn("\n{}\nHTTP EXECUTION FAILED\n"
                            + "Test case      : {}\n"
                            + "Step           : {}\n"
                            + "Test ID        : {}\n"
                            + "Correlation ID : {}\n"
                            + "Elapsed        : {} ms\n"
                            + "Failure        : {}\n{}",
                    DIVIDER, currentTestCase(), currentStep(), testId, correlationId,
                    elapsedMillis, failure.getClass().getName(), DIVIDER);
            throw failure; // Preserve the same exception, including its original stack.
        }

        // A 400/500 is a received response, not an execution exception or automatic
        // test failure. Tests own status expectations. The response's elapsed time
        // is measured by the transport; the failure duration above is measured here.
        LOG.info("\n{}\nHTTP RESPONSE\n"
                        + "Test case      : {}\n"
                        + "Step           : {}\n"
                        + "Test ID        : {}\n"
                        + "Correlation ID : {}\n"
                        + "Status         : {}\n"
                        + "Duration       : {} ms\n"
                        + "Headers        : {}\n"
                        + "Body           : {}\n{}",
                DIVIDER, currentTestCase(), currentStep(), testId, correlationId,
                response.statusCode(), response.elapsedTime().toMillis(),
                redactor.headers(response.headers()), formatResponseBody(response), DIVIDER);

        return response;
    }

    private String formatRequestBody(HttpRequest request) {
        if (!request.hasBody()) {
            return "<none>";
        }
        if (bodyFormatter == null) {
            return "<body logging disabled>";
        }
        return bodyFormatter.format(
                request.body(), request.headers().firstValue("Content-Type").orElse(null));
    }

    private String formatResponseBody(HttpResponse response) {
        if (response.bodyLength() == 0) {
            return "<none>";
        }
        if (bodyFormatter == null) {
            return "<body logging disabled>";
        }
        return bodyFormatter.format(response.body(), response.contentType().orElse(null));
    }

    private static String currentTestCase() {
        return mdcValue("testCase", "<not available>");
    }

    private static String currentStep() {
        return mdcValue("testStep", "<not specified>");
    }

    private static String mdcValue(String key, String fallback) {
        String value = MDC.get(key);
        return value == null ? fallback : singleLine(value);
    }

    /** Keeps caller-supplied IDs from inserting new lines into a summary log event. */
    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
