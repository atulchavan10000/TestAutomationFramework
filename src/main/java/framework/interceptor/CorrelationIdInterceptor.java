package framework.interceptor;

import framework.context.TestContext;
import framework.http.HttpHeaders;
import framework.http.HttpRequest;
import framework.http.HttpResponse;

import java.util.Objects;

/**
 * Adds a correlation header using the context of one test execution.
 *
 * Example setup:
 * TestContext context = new TestContext("execution-123");
 * HttpInterceptor correlation = new CorrelationIdInterceptor(context);
 *
 * Create an interceptor for each test context, and reuse it for that test's
 * requests. Do not place this context-bound interceptor in a globally shared
 * chain used by unrelated tests. The test lifecycle wiring must provide scope.
 *
 * Proposed override policy:
 * - An existing request header wins, including an empty or multiple-valued header.
 * - That override applies only to that request; it does not replace the context ID.
 * - Otherwise, use the context's lazily generated ID, reused across the test.
 *
 * X-Correlation-ID is our default convention, not a universal HTTP requirement.
 * Supply another header name when the service uses a different convention.
 * CommonHeadersInterceptor should not also supply this header: it is owned by
 * this interceptor and explicit request options, to avoid conflicting defaults.
 */
public final class CorrelationIdInterceptor implements HttpInterceptor {

    // A shared constant is fine: the value is immutable and contains no test state.
    private static final String DEFAULT_HEADER_NAME = "X-Correlation-ID";

    // These references belong to this interceptor instance, not to the class.
    private final TestContext context;
    private final String headerName;

    /** Uses our default header name. Delegates validation to the other constructor. */
    public CorrelationIdInterceptor(TestContext context) {
        this(context, DEFAULT_HEADER_NAME);
    }

    /** Receives the test context and the correlation header name used by the service. */
    public CorrelationIdInterceptor(TestContext context, String headerName) {
        this.context = Objects.requireNonNull(context, "Test context must not be null");
        this.headerName = Objects.requireNonNull(headerName, "Correlation header name must not be null");
        if (headerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Correlation header name must not be blank");
        }
    }

    /** Preserves an explicit header, or adds a context-derived header, then continues. */
    @Override
    public HttpResponse intercept(HttpRequest request, InterceptorChain chain) {
        Objects.requireNonNull(request, "HTTP request must not be null");
        Objects.requireNonNull(chain, "Interceptor chain must not be null");

        // contains() ignores casing. Do not replace deliberate test inputs or
        // generate an unused context ID when the request already has the header.
        if (request.headers().contains(headerName)) {
            return chain.proceed(request);
        }

        // This creates the ID only on first use; subsequent calls reuse it.
        String correlationId = context.getOrCreateCorrelationId();

        // Both the header collection and request are immutable. Build new copies
        // rather than changing objects another component might still reference.
        HttpHeaders headers = request.headers().toBuilder()
                .set(headerName, correlationId)
                .build();

        HttpRequest outgoing = request.toBuilder()
                .headers(headers)
                .build();

        // Each path delegates exactly once. Return the response unchanged;
        // downstream exceptions propagate to the caller naturally.
        return chain.proceed(outgoing);
    }
}
