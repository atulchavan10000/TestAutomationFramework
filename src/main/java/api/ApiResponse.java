package api;

import java.util.Objects;

/**
 * Pairs a typed application body with the original HTTP response.
 *
 * R is the response-body type, for example CreateUserResponse.
 * Unlike HttpResponse, this class knows the Java type selected for mapping.
 * It still does not serialize, deserialize, send requests, or run assertions.
 * ApiClient coordinates mapping through JsonCodec and constructs this result.
 *
 * Example (assuming raw and mappedUser were produced by execution and mapping):
 * ApiResponse<CreateUserResponse> result = new ApiResponse<>(raw, mappedUser);
 * CreateUserResponse user = result.body();
 * int status = result.rawResponse().statusCode();
 * byte[] originalContent = result.rawResponse().body();
 *
 * For a deliberately unmapped result or an operation expecting no typed body:
 * ApiResponse<Void> result = new ApiResponse<>(raw, null);
 *
 * Initial contract for review: body() may return null when no mapped value is
 * available by design (empty content, mapping skipped, or a mapped JSON null).
 * This simple holder does not distinguish those cases. A mapping failure must
 * NOT be silently converted to null; that policy remains to be designed in
 * ApiClient, with access to the original HttpResponse preserved on failure.
 */
public final class ApiResponse<T> {

    private final HttpResponse rawResponse;
    private final T body;

    /**
     * Creates the result after HTTP execution and any requested body mapping.
     * Only two values are needed, so a builder would add unnecessary machinery.
     *
     * @param rawResponse the original response, always required
     * @param body the mapped Java value, or null when there is no typed value
     */
    public ApiResponse(HttpResponse rawResponse, T body) {
        // Even when there is no typed body, status, headers, timing, and raw
        // content must remain available for assertions and diagnostics.
        this.rawResponse = Objects.requireNonNull(
                rawResponse, "Raw HTTP response must not be null");

        // A typed body is not required: an empty response is a legitimate result.
        // Do not apply requireNonNull here just because rawResponse requires it.
        this.body = body;
    }

    /**
     * Returns the original immutable HTTP response, including its body bytes.
     * Sharing this reference is safe because HttpResponse protects its contents.
     */
    public HttpResponse rawResponse() {
        return rawResponse;
    }

    /**
     * Returns the mapped application value, or null when no typed value exists.
     * The generic R makes the return type match ApiResponse<R> without a cast.
     *
     * This returns the actual DTO reference, not a deep copy. If R is a mutable
     * DTO, its fields can still change. final fixes this wrapper's reference;
     * it cannot make an arbitrary application object immutable or thread-safe.
     * Prefer immutable DTOs if the complete result needs to be safely shared.
     * Changing the DTO does not alter the preserved raw response bytes.
     */
    public T body() {
        return body;
    }
}
