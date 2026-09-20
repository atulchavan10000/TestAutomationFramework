package api;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * An immutable snapshot of an HTTP result returned by the transport adapter.
 *
 * It preserves status, headers, body bytes, and elapsed time. It does not know
 * about application DTOs, expected status codes, JSONPath, or test assertions.
 *
 * A 400 or 500 response is still an HTTP response and belongs in this class.
 * A connection failure with no HTTP response should be handled separately;
 * the adapter must not invent a status code or empty response to hide it.
 *
 * Example (normally the adapter supplies these values):
 * HttpResponse response = new HttpResponse(
 *     201,
 *     HttpHeaders.builder().set("Content-Type", "application/json").build(),
 *     "{\"id\":42}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
 *     Duration.ofMillis(125)
 * );
 *
 * int status = response.statusCode();
 * byte[] content = response.body();
 *
 * Use the HttpHeaders implementation from the earlier temporary example.
 */
public final class HttpResponse {

    private final int statusCode;
    private final HttpHeaders headers;
    private final byte[] body;
    private final Duration elapsedTime;

    /**
     * Creates a snapshot from the values captured by the adapter.
     *
     * A constructor is enough here: there are only four required values, and
     * the adapter has them together after execution. A builder would add little.
     *
     * @param statusCode the actual received status; no success expectation is applied
     * @param headers immutable headers; use HttpHeaders.empty() if none were received
     * @param body received body bytes; use new byte[0] when there is no content
     * @param elapsedTime nonnegative duration for this transport attempt
     */
    public HttpResponse(
            int statusCode,
            HttpHeaders headers,
            byte[] body,
            Duration elapsedTime) {

        this.statusCode = statusCode;
        this.headers = Objects.requireNonNull(headers, "Response headers must not be null");

        // The adapter owns its input array. Copy it so later changes by the
        // adapter cannot alter the response evidence kept by tests or logging.
        this.body = Objects.requireNonNull(body, "Response body must not be null").clone();

        this.elapsedTime = Objects.requireNonNull(
                elapsedTime, "Response elapsed time must not be null");

        // Zero is allowed: very fast calls or coarse measurement can produce it.
        if (elapsedTime.isNegative()) {
            throw new IllegalArgumentException("Response elapsed time must not be negative");
        }
    }

    /**
     * Returns the received status without interpreting it as a test pass/failure.
     * Tests decide whether that status is expected for their scenario.
     */
    public int statusCode() {
        return statusCode;
    }

    /** Returns immutable headers, supporting case-insensitive lookup and custom names. */
    public HttpHeaders headers() {
        // HttpHeaders is immutable, so sharing this reference is safe.
        return headers;
    }

    /**
     * Returns a fresh copy of the body on every call, never null.
     * An empty array means there are no captured body bytes. This model does not
     * distinguish an absent body from an explicitly zero-length response body.
     *
     * The bytes may contain JSON, XML, text, binary data, or malformed content.
     * They are the bytes exposed by the HTTP library, not necessarily an exact
     * wire capture: the library may already have removed framing or decompressed.
     */
    public byte[] body() {
        // Returning the internal array would let a caller change this response.
        return body.clone();
    }

    /** Returns the body byte count without allocating a copy of the body array. */
    public int bodyLength() {
        return body.length;
    }

    /**
     * Returns the first Content-Type value, or Optional.empty() if none exists.
     * This reads the headers instead of storing a duplicate content-type field.
     * It does not parse the media type, infer a charset, or validate the content.
     * All values remain available through headers().values("Content-Type").
     */
    public Optional<String> contentType() {
        return headers.firstValue("Content-Type");
    }

    /**
     * Returns the duration measured by the adapter for this one transport attempt.
     * Proposed measurement: start immediately before transport execution and stop
     * after reading the full body. Use a monotonic clock in the adapter.
     * Request preparation, DTO mapping, and later retry attempts are excluded.
     * This class stores a measurement; it does not perform or enforce timing.
     */
    public Duration elapsedTime() {
        // Duration is immutable and can safely be returned directly.
        return elapsedTime;
    }
}
