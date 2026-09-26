package framework.interceptor;

import framework.http.HttpRequest;
import framework.http.HttpResponse;

/**
 * A component that participates before and/or after an HTTP request executes.
 * Examples include common headers, correlation IDs, and request/response logging.
 *
 * ApiClient prepares the request. The chain passes it through interceptors and
 * eventually invokes HttpClient. DTO serialization/mapping stays in ApiClient.
 *
 * Typical implementation shape:
 *
 * public HttpResponse intercept(HttpRequest request, InterceptorChain chain) {
 *     // Before: inspect the request or build a modified copy.
 *     HttpResponse response = chain.proceed(request);
 *     // After: inspect the response returned by downstream execution.
 *     return response;
 * }
 *
 * Returning from proceed() means the downstream work has completed, so code
 * after that call can inspect its result. If downstream execution throws, that
 * code is skipped unless the interceptor handles the failure with try/catch.
 * Use finally for cleanup that must happen on either success or failure.
 */
public interface HttpInterceptor {

    /**
     * Participates in one synchronous execution and returns its HTTP response.
     *
     * @param request non-null immutable request at this stage of processing
     * @param chain continuation for the remaining interceptors and transport
     * @return a non-null HTTP response
     *
     * For the initial header/correlation/logging interceptors, call proceed()
     * exactly once and return the downstream response. Header changes require
     * request.toBuilder() and headers.toBuilder(); do not mutate existing objects.
     *
     * Do not retain the request's chain in an instance/static field or share it
     * with another thread. It belongs to this execution. Shared interceptor
     * instances must keep per-call mutable state local or in a scoped context.
     *
     * This interface alone does not enforce call counts or thread safety.
     * Retry/replay and short-circuit behavior require explicit policies later.
     */
    HttpResponse intercept(HttpRequest request, InterceptorChain chain);
}
