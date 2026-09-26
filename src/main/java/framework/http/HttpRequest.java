package framework.http;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * An immutable, prepared HTTP request for the framework's HTTP adapter.
 *
 * ApiClient prepares the complete URI and serializes the body before building
 * this object. This class does not serialize DTOs or make network calls.
 * Content-Type is stored in headers, so there is no second competing field.
 *
 * Example (the URL and JSON below are illustrative):
 * HttpRequest request = HttpRequest.builder()
 *     .method(HttpMethod.POST)
 *     .uri(URI.create("https://example.test/users"))
 *     .headers(HttpHeaders.builder()
 *         .set("Content-Type", "application/json")
 *         .build())
 *     .body("{\"name\":\"Alice\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8))
 *     .timeout(Duration.ofSeconds(30))
 *     .build();
 *
 * This is an example contract for review, not a complete transport implementation.
 */
public final class HttpRequest {

    private final HttpMethod method;
    private final URI uri;
    private final HttpHeaders headers;

    // null means no body; new byte[0] means an explicitly supplied empty body.
    // Arrays are mutable, so they need copies even when the field is final.
    private final byte[] body;

    private final Duration timeout;

    /** Validates required settings and takes a snapshot of the builder's data. */
    private HttpRequest(Builder builder) {
        this.method = Objects.requireNonNull(builder.method, "HTTP method is required");
        this.uri = Objects.requireNonNull(builder.uri, "Request URI is required");
        this.headers = Objects.requireNonNull(builder.headers, "Headers must not be null");
        this.timeout = Objects.requireNonNull(builder.timeout, "Request timeout is required");

        // This model represents a prepared destination, not an unresolved path.
        String scheme = uri.getScheme();

        boolean unsupportedScheme = !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        boolean missingHost = uri.getHost() == null;
        boolean hasFragment = uri.getRawFragment() != null;

        if (unsupportedScheme || missingHost || hasFragment) {
            throw new IllegalArgumentException(
                    "URI must be an absolute HTTP(S) URL with a host and no fragment");
        }

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Request timeout must be positive");
        }

        // Snapshot the array so subsequent builder changes cannot affect us.
        this.body = copyBody(builder.body);
    }

    /** Creates a builder; method, URI, and timeout must be supplied before build(). */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the framework's HTTP method enum, not a library-specific type. */
    public HttpMethod method() {
        return method;
    }

    /** Returns the complete URI, including already-resolved path and query values. */
    public URI uri() {
        // URI is immutable, so it is safe to return the stored reference.
        return uri;
    }

    /** Returns immutable headers; callers can use headers().toBuilder() to edit a copy. */
    public HttpHeaders headers() {
        return headers;
    }

    /**
     * Indicates whether a body was supplied, including a zero-length body.
     * It does not validate the payload or restrict bodies based on HTTP method.
     */
    public boolean hasBody() {
        return body != null;
    }

    /**
     * Returns a new copy of the body bytes, or null if no body was supplied.
     * Modifying the returned array cannot change this request.
     * The bytes may contain JSON, text, binary data, or deliberately malformed data.
     */
    public byte[] body() {
        return copyBody(body);
    }

    /**
     * Returns the configured timeout for one transport attempt.
     * Proposed meaning: maximum elapsed time until the full response is received.
     * It excludes serialization and any later retry attempts. The adapter must
     * implement this contract; storing a Duration does not itself enforce a timer.
     * Exact adapter support must be settled before using this in the working slice.
     */
    public Duration timeout() {
        // Duration is immutable, so sharing it is safe.
        return timeout;
    }

    /**
     * Starts a builder with the current request's settings.
     * Interceptors can change that builder and build a new request while the
     * original request remains unchanged.
     */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** Copies mutable bytes, preserving the distinction between absent and empty. */
    private static byte[] copyBody(byte[] source) {
        return source == null ? null : source.clone();
    }

    /** Mutable construction helper; do not share a builder between threads. */
    public static final class Builder {

        private HttpMethod method;
        private URI uri;
        private HttpHeaders headers = HttpHeaders.empty();
        private byte[] body;
        private Duration timeout;

        private Builder() {}

        /** Shares immutable objects, but copies the mutable byte array. */
        private Builder(HttpRequest source) {
            this.method = source.method;
            this.uri = source.uri;
            this.headers = source.headers;
            this.body = copyBody(source.body);
            this.timeout = source.timeout;
        }

        /** Sets the method; returning this lets the next builder call be chained. */
        public Builder method(HttpMethod method) {
            this.method = Objects.requireNonNull(method, "HTTP method must not be null");
            return this;
        }

        /** Sets an already-prepared URI; this method does not encode parameters. */
        public Builder uri(URI uri) {
            this.uri = Objects.requireNonNull(uri, "Request URI must not be null");
            return this;
        }

        /** Replaces the complete header collection; it does not merge defaults. */
        public Builder headers(HttpHeaders headers) {
            this.headers = Objects.requireNonNull(headers, "Headers must not be null");
            return this;
        }

        /**
         * Sets prepared body bytes and copies them immediately.
         * This prevents changes to the caller's array even BEFORE build() is called.
         * Use withoutBody() to remove a previously supplied body.
         */
        public Builder body(byte[] body) {
            this.body = copyBody(Objects.requireNonNull(body, "Body must not be null"));
            return this;
        }

        /** Removes the body; this differs from setting an empty byte array. */
        public Builder withoutBody() {
            this.body = null;
            return this;
        }

        /** Sets the effective timeout chosen by request preparation/configuration. */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "Request timeout must not be null");
            return this;
        }

        /** Validates the settings and returns a new, immutable request snapshot. */
        public HttpRequest build() {
            return new HttpRequest(this);
        }
    }
}
