package framework.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Objects;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Jackson 2.x implementation of our JsonCodec interface.
 * Requires jackson-databind and its jackson-core/jackson-annotations dependencies.
 * The project has not yet selected a dependency version; this example does not
 * prescribe one or change the project's build file.
 *
 * ApiClient uses the JsonCodec interface; only this adapter imports Jackson.
 * This implementation is for ordinary bean DTOs with Jackson-readable properties
 * and a suitable constructor. Java time modules and advanced mapping are deferred.
 *
 * Example:
 * JsonCodec codec = new JacksonJsonCodec();
 * byte[] json = codec.serialize(requestDto);
 * CreateUserResponse dto = codec.deserialize(responseBytes, CreateUserResponse.class);
 */
public final class JacksonJsonCodec implements JsonCodec {

    // ObjectMapper performs the actual JSON conversion. final fixes the reference;
    // it does not make ObjectMapper immutable. We configure it only at construction
    // and do not expose it or change its configuration during execution.
    private final ObjectMapper mapper;

    /** Creates and configures one reusable mapper, rather than one per request. */
    public JacksonJsonCodec() {
        // Configure through the builder before using the mapper. Direct
        // ObjectMapper.disable(MapperFeature...) is deprecated since Jackson 2.13.
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                // Reject extra content after the first JSON value, e.g. {} {}.
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                // Reject JSON properties missing from the DTO (initial policy).
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                // Reject conversions such as JSON "42" into Integer.
                // This is not a replacement for complete schema validation.
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();

        // The service contracts serialize exact decimal values as JSON strings,
        // for example "125.50". Permit that documented representation only for
        // BigDecimal while continuing to reject unrelated scalar conversions.
        this.mapper.coercionConfigFor(BigDecimal.class)
                .setCoercion(CoercionInputShape.String, CoercionAction.TryConvert);
    }

    /**
     * Serializes a Java value directly to UTF-8 JSON bytes.
     * null intentionally produces bytes for the JSON literal null.
     * Already-prepared JSON must bypass this method: serializing a String quotes
     * and escapes it as a JSON string rather than treating it as raw JSON.
     */
    @Override
    public byte[] serialize(Object value) {
        try {
            return mapper.writeValueAsBytes(value);
        } catch (JsonProcessingException cause) {
            // Preserve the original cause for diagnosis. A framework-specific
            // exception can replace IllegalArgumentException once that contract
            // is designed. Callers need not import Jackson exception types.
            throw new IllegalArgumentException("Could not serialize value to JSON", cause);
        }
    }

    /**
     * Maps a complete JSON document into the requested reference type.
     * Jackson detects supported JSON byte encodings; no platform-default String
     * conversion is performed here. Empty/whitespace-only input is a mapping error.
     * JSON null can legitimately return null; malformed content must throw.
     */
    @Override
    public <T> T deserialize(byte[] json, Class<T> targetType) {
        Objects.requireNonNull(json, "JSON bytes must not be null");
        Objects.requireNonNull(targetType, "Target type must not be null");

        // Boxed types allow Java null to represent the JSON literal null.
        // Reject primitives rather than letting null silently become 0 or false.
        if (targetType.isPrimitive()) {
            throw new IllegalArgumentException(
                    "Use a reference type such as Integer.class, not int.class");
        }

        try {
            return mapper.readValue(json, targetType);
        } catch (IOException cause) {
            // readValue(byte[], Class) declares IOException even though its input
            // is already in memory. Jackson's parsing/mapping exceptions are also
            // covered by this catch. Do not convert these failures to null.
            throw new IllegalArgumentException(
                    "Could not deserialize JSON into " + targetType.getTypeName(), cause);
        }
    }
}
