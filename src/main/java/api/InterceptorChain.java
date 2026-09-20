package api;

/**
 * Represents the remaining work after the current interceptor.
 * DefaultInterceptorChain will supply the execution logic behind this contract.
 * An interceptor needs only proceed(), not the entire interceptor list or client.
 */
public interface InterceptorChain {

    /**
     * Passes a request to the next interceptor. Once no interceptors remain,
     * execution reaches HttpClient.execute(request).
     *
     * @param request non-null request, possibly a modified copy from an interceptor
     * @return the non-null response produced by downstream execution
     *
     * This is a synchronous call: it returns after downstream work completes.
     * Responses return through the same calls in reverse order:
     *
     * Request:  A -> B -> HttpClient
     * Response: A <- B <- HttpClient
     *
     * Transport or downstream failures propagate as exceptions unless explicitly
     * handled. Do not replace a failure with null or a fabricated HTTP status.
     */
    HttpResponse proceed(HttpRequest request);
}
