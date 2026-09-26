package framework.serialization;
/** Mapping/serialization failed. The original library exception is retained as cause. */
public final class JsonCodecException extends RuntimeException {
    public JsonCodecException(String message, Throwable cause) { super(message, cause); }
}
