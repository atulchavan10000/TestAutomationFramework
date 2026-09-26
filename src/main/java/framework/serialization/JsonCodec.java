package framework.serialization;

/**
 * Framework-owned contract for converting between Java values and JSON bytes.
 * "Codec" combines encoding (serialization) and decoding (deserialization).
 *
 * JacksonJsonCodec will implement this interface using Jackson. Keeping Jackson
 * types out of these signatures lets ApiClient depend on our mapping contract.
 *
 * This interface does not send requests, choose HTTP headers, inspect status
 * codes, or make assertions. ApiClient decides when JSON mapping is appropriate.
 * Deliberately raw request bytes bypass serialize() rather than being serialized
 * again. A Java String passed to serialize() becomes a JSON string value, not
 * an unchanged JSON document.
 *
 * These are proposed initial mapping contracts. A dedicated framework exception
 * and a type descriptor for parameterized targets remain to be designed.
 */
public interface JsonCodec {

    /**
     * Converts a Java value into a complete JSON document encoded as UTF-8 bytes.
     * These bytes can be placed directly in HttpRequest's body.
     *
     * Example: a DTO representing a user becomes bytes for {"name":"Alice"}.
     * Passing null represents the JSON literal null; it does not mean no HTTP
     * body. ApiClient must skip serialization when the request has no body.
     *
     * @param value Java value to serialize, including null for JSON null
     * @return non-null UTF-8 JSON bytes
     *
     * Serialization failures must be reported as exceptions, never an empty
     * array or fabricated JSON. The implementation must not modify the input DTO.
     */
    byte[] serialize(Object value);

    /**
     * Converts JSON bytes into a value of the requested Java type.
     *
     * Example:
     * CreateUserResponse user = codec.deserialize(bytes, CreateUserResponse.class);
     *
     * The method declares its own type parameter R. Class<T> supplies the runtime
     * target type and connects it to the return type, so callers need no cast.
     * JsonCodec itself is not generic: one codec can handle many DTO types.
     *
     * @param json non-null bytes containing a JSON document; never modified
     * @param targetType non-null reference type to construct (use boxed types
     *                   such as Integer.class rather than primitive int.class)
     * @return the mapped value, potentially null for the JSON literal null
     *
     * Empty input, malformed JSON, and incompatible mappings must produce an
     * exception, not silently return null. ApiClient handles genuinely empty
     * HTTP bodies or intentionally skipped mapping before invoking this method.
     * The concrete implementation's encoding and mapping settings must be
     * documented; they are not selected by this interface alone.
     *
     * Class<T> handles simple DTO targets and arrays. It cannot fully describe
     * List<CreateUserResponse>; a framework-owned generic type descriptor is a
     * later extension. Do not use raw List.class and claim typed element safety.
     */
    <T> T deserialize(byte[] json, Class<T> targetType);
}
