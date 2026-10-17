package framework.client;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
/**
 * Explicit escape hatch for prepared bytes, including malformed JSON.
 * Wrap raw content in this type rather than guessing from String or byte[].
 */
public final class RawBody {
    private final byte[] bytes;
    private final String contentType;

    /** Creates a defensive snapshot of caller-prepared bytes and their media type. */
    public RawBody(byte[] bytes, String contentType) {
        this.bytes = Objects.requireNonNull(bytes).clone();
        this.contentType = Objects.requireNonNull(contentType);
    }
    /** Creates UTF-8 JSON bytes without validating or parsing the supplied text. */
    public static RawBody json(String json) {
        return new RawBody(Objects.requireNonNull(json).getBytes(StandardCharsets.UTF_8), "application/json");
    }
    /** Carries a UTF-8 HTML form body for OAuth and similar non-JSON endpoints. */
    public static RawBody form(String encodedForm) {
        return new RawBody(Objects.requireNonNull(encodedForm).getBytes(StandardCharsets.UTF_8),
                "application/x-www-form-urlencoded");
    }
    /** Returns a defensive copy so callers cannot mutate the stored payload. */
    public byte[] bytes() { return bytes.clone(); }

    /** Returns the media type that ApiClient should apply when no override exists. */
    public String contentType() { return contentType; }
}
