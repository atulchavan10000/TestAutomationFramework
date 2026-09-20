package api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs interceptors in their supplied order, then delegates to HttpClient.
 *
 * Example:
 * InterceptorChain chain = new DefaultInterceptorChain(interceptors, httpClient);
 * HttpResponse response = chain.proceed(preparedRequest);
 *
 * Each chain object represents a fixed position in the interceptor list.
 * proceed() creates a new chain at the next position; it never increments a
 * shared counter. No request or response is stored in an instance field.
 *
 * This does not automatically make the interceptors or client thread-safe.
 * Their own state and lifecycle still need to be managed correctly.
 */
public final class DefaultInterceptorChain implements InterceptorChain {

    private final List<HttpInterceptor> interceptors;
    private final HttpClient httpClient;
    private final int index;

    /** Creates the starting chain, taking a snapshot of interceptor registration. */
    public DefaultInterceptorChain(List<HttpInterceptor> interceptors, HttpClient httpClient) {
        Objects.requireNonNull(interceptors, "Interceptor list must not be null");
        this.httpClient = Objects.requireNonNull(httpClient, "HTTP client must not be null");

        List<HttpInterceptor> copy = new ArrayList<>();
        for (HttpInterceptor interceptor : interceptors) {
            copy.add(Objects.requireNonNull(interceptor, "Interceptor must not be null"));
        }

        // Protect list membership and order from later caller changes.
        // The interceptor objects themselves are not cloned or made immutable.
        this.interceptors = Collections.unmodifiableList(copy);
        this.index = 0;
    }

    /**
     * Creates a continuation at the next position. Only this class calls it.
     * The already-protected list is shared rather than copied at every step.
     */
    private DefaultInterceptorChain(
            List<HttpInterceptor> interceptors, HttpClient httpClient, int index) {
        this.interceptors = interceptors;
        this.httpClient = httpClient;
        this.index = index;
    }

    /**
     * Executes the remaining work synchronously.
     * For [A, B], calls nest as A -> B -> client, then return B -> A.
     * That ordinary method-call nesting provides the before/after behavior;
     * a separate reverse loop is unnecessary.
     */
    @Override
    public HttpResponse proceed(HttpRequest request) {
        Objects.requireNonNull(request, "HTTP request must not be null");

        // Base case: every interceptor has run, or the list was empty.
        if (index == interceptors.size()) {
            return Objects.requireNonNull(
                    httpClient.execute(request), "HTTP client returned null");
        }

        HttpInterceptor current = interceptors.get(index);

        // Pass the NEXT position to the current interceptor. Passing 'this'
        // instead would invoke the same interceptor again and again.
        InterceptorChain next = new DefaultInterceptorChain(
                interceptors, httpClient, index + 1);

        // The interceptor decides when to call next.proceed(request), and may
        // pass a modified request copy. Its return value propagates to its caller.
        // Exceptions also propagate naturally; we do not fabricate a response.
        return Objects.requireNonNull(
                current.intercept(request, next), "HTTP interceptor returned null");
    }

    // Initial interceptors should call their continuation exactly once.
    // This implementation does not enforce that count: calling a continuation
    // twice executes the downstream work twice, potentially sending two requests.
    // Retry/replay and short-circuit policies remain separate future decisions.
}
