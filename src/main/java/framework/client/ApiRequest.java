package framework.client;

import framework.http.HttpMethod;

import java.util.Objects;

/**
 * Describes an application operation before it is prepared for HTTP transport.
 *
 * UserApi creates this object. ApiClient resolves the destination, coordinates
 * serialization, applies options/defaults, and constructs HttpRequest.
 *
 * Example (illustrative path; actual operations come from the API contract):
 * ApiRequest request = new ApiRequest(
 *     HttpMethod.POST,
 *     "/teams/{teamId}/users",
 *     requestDto,
 *     RequestOptions.builder().pathParam("teamId", "42").build()
 * );
 *
 * The response type is intentionally separate:
 * ApiResponse<CreateUserResponse> result =
 *     apiClient.execute(request, CreateUserResponse.class);
 *
 * A normal body is serialized through JsonCodec. RawBody explicitly carries
 * prepared bytes that bypass serialization. Strings are ordinary JSON strings,
 * never implicitly raw JSON.
 */
public final class ApiRequest {

    private final HttpMethod method;
    private final String path;
    private final Object body;
    private final RequestOptions options;

    /**
     * Creates an operation description with explicit request options.
     *
     * @param method the operation's HTTP method
     * @param path operation path/template, before placeholder resolution
     * @param body Java value to serialize, or null to send no body
     * @param options caller-supplied options; use RequestOptions.empty() for none
     */
    public ApiRequest(HttpMethod method, String path, Object body, RequestOptions options) {
        this.method = Objects.requireNonNull(method, "HTTP method must not be null");
        this.path = Objects.requireNonNull(path, "Operation path must not be null");
        if (path.trim().isEmpty()) {
            throw new IllegalArgumentException("Operation path must not be blank");
        }

        // null is valid here: GET/DELETE and other operations may have no body.
        // To explicitly send JSON null, use RawBody.json("null").
        this.body = body;
        this.options = Objects.requireNonNull(options, "Request options must not be null");
    }

    /** Convenience constructor for calls without request-specific overrides. */
    public ApiRequest(HttpMethod method, String path, Object body) {
        this(method, path, body, RequestOptions.empty());
    }

    /** Returns the method that UserApi selected for this operation. */
    public HttpMethod method() {
        return method;
    }

    /**
     * Returns the unresolved operation path, such as /teams/{teamId}/users.
     * It is a String rather than URI because placeholders may not be valid URI
     * characters yet. Base URL joining, validation, and encoding happen during
     * request preparation. This class neither resolves nor modifies the path.
     */
    public String path() {
        return path;
    }

    /**
     * Returns the Java value to serialize, or null for no body.
     * Object lets the framework carry different request DTO classes; UserApi's
     * operation methods provide the specific parameter types for test authors.
     *
     * This returns the original DTO reference, not a deep copy. final prevents
     * replacing the reference but does not freeze a mutable DTO. Do not mutate
     * the DTO while execution/serialization is in progress.
     */
    public Object body() {
        return body;
    }

    /** Returns whether this initial operation description includes a body value. */
    public boolean hasBody() {
        return body != null;
    }

    /** Returns immutable caller options; these are not yet the final merged settings. */
    public RequestOptions options() {
        return options;
    }
}
