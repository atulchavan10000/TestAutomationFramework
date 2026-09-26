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
    public RawBody(byte[] bytes, String contentType) {
        this.bytes = Objects.requireNonNull(bytes).clone();
        this.contentType = Objects.requireNonNull(contentType);
    }
    public static RawBody json(String json) {
        return new RawBody(Objects.requireNonNull(json).getBytes(StandardCharsets.UTF_8), "application/json");
    }
    public byte[] bytes() { return bytes.clone(); }
    public String contentType() { return contentType; }
}
