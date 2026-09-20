package api;

/**
 * Framework-owned contract for executing one prepared HTTP request.
 * This is our api.HttpClient, not java.net.http.HttpClient.
 *
 * RestAssuredHttpClient will implement this interface. Higher layers depend on
 * this contract instead of REST Assured request/response classes, so another
 * transport implementation can be substituted without changing those layers.
 *
 * The implementation performs the network exchange and adapts the library's
 * result into our HttpResponse. It does not map application DTOs, assert test
 * expectations, refresh authentication, or apply the framework's retry policy.
 * ApiClient and the interceptor chain coordinate those other responsibilities.
 */
public interface HttpClient {

    /**
     * Executes one prepared request synchronously and returns the HTTP result.
     *
     * @param request non-null request with a resolved URI and prepared body bytes
     * @return a non-null response, including HTTP error results such as 400 or 500
     *
     * A network failure with no usable response must be reported as an exception,
     * not null or a fabricated HTTP status. The framework's exact transport
     * exception type remains to be designed when implementing the adapter.
     *
     * Interface methods are implicitly public and abstract: each implementation
     * supplies the method body. This declaration itself makes no network call.
     */
    HttpResponse execute(HttpRequest request);
}
