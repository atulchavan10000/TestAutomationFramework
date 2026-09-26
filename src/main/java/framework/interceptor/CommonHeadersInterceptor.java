package framework.interceptor;

import framework.http.HttpHeaders;
import framework.http.HttpRequest;
import framework.http.HttpResponse;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Adds configured default headers when the request does not already contain them.
 * Explicit request values always take precedence, even when their spelling uses
 * different casing or their value is an empty string for a negative test.
 *
 * Example setup:
 * HttpHeaders defaults = HttpHeaders.builder()
 *     .set("Accept", "application/json")
 *     .set("X-Test-Client", "automation")
 *     .build();
 * HttpInterceptor commonHeaders = new CommonHeadersInterceptor(defaults);
 *
 * This class does not load configuration, generate authentication tokens, or
 * decide which headers are appropriate for every operation. Setup supplies the
 * applicable defaults. For example, do not blindly assume every body is JSON
 * by hardcoding Content-Type here; request preparation knows the body format.
 */
public final class CommonHeadersInterceptor implements HttpInterceptor {

    // Immutable defaults can be reused across requests without per-call mutation.
    private final HttpHeaders defaults;

    /** Receives defaults selected by framework setup/configuration. */
    public CommonHeadersInterceptor(HttpHeaders defaults) {
        this.defaults = Objects.requireNonNull(defaults, "Default headers must not be null");
    }

    /**
     * Fills missing headers, then continues execution exactly once.
     * Only header presence matters: an explicitly supplied value is not replaced
     * merely because it differs from a default or looks unusual.
     */
    @Override
    public HttpResponse intercept(HttpRequest request, InterceptorChain chain) {
        Objects.requireNonNull(request, "HTTP request must not be null");
        Objects.requireNonNull(chain, "Interceptor chain must not be null");

        HttpHeaders suppliedHeaders = request.headers();

        // Each call gets its own builder. It is never stored in an instance field
        // or shared across requests, so this interceptor has no mutable call state.
        HttpHeaders.Builder mergedHeaders = suppliedHeaders.toBuilder();
        boolean changed = false;

        for (Map.Entry<String, List<String>> entry : defaults.asMap().entrySet()) {
            String name = entry.getKey();

            // HttpHeaders.contains() handles case-insensitive matching.
            // A supplied header wins as a whole: do not append default values
            // to its existing values, as that could change the caller's intent.
            if (!suppliedHeaders.contains(name)) {
                for (String value : entry.getValue()) {
                    // add() preserves all default values and their order.
                    // Repeated set() calls would retain only the last value.
                    mergedHeaders.add(name, value);
                }
                changed = true;
            }
        }

        // HttpRequest is immutable. Build a modified copy only if defaults were
        // added. method, URI, body, and timeout are retained by toBuilder().
        HttpRequest outgoing = request;
        if (changed) {
            outgoing = request.toBuilder()
                    .headers(mergedHeaders.build())
                    .build();
        }

        // This interceptor has no response processing: return downstream evidence
        // unchanged. Downstream exceptions propagate naturally.
        return chain.proceed(outgoing);
    }
}
