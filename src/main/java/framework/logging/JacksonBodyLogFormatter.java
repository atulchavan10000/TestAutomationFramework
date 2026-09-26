package framework.logging;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.*;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.*;

/**
 * Bounded text/JSON logging with recursive JSON field redaction.
 * Malformed JSON is omitted when it cannot be safely redacted; raw bytes remain
 * available in HttpResponse. Binary/unknown types are summarized, never decoded.
 * Text content is not field-redacted; enable it only for suitable test data.
 */
public final class JacksonBodyLogFormatter implements BodyLogFormatter {
    private static final Pattern CHARSET = Pattern.compile("charset\\s*=\\s*\"?([^;\"\\s]+)", Pattern.CASE_INSENSITIVE);
    private final ObjectMapper mapper = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final int maxInputBytes, maxOutputChars;
    private final Set<String> redactedFields;
    public JacksonBodyLogFormatter(int maxInputBytes, int maxOutputChars, Set<String> redactedFields) {
        if (maxInputBytes < 1 || maxOutputChars < 1) throw new IllegalArgumentException("Limits must be positive");
        this.maxInputBytes = maxInputBytes; this.maxOutputChars = maxOutputChars;
        Set<String> fields = new HashSet<>();
        for (String field : redactedFields) fields.add(field.toLowerCase(Locale.ROOT));
        this.redactedFields = Set.copyOf(fields);
    }
    public static JacksonBodyLogFormatter defaults() {
        return new JacksonBodyLogFormatter(65536, 4096,
                Set.of("password", "password_hash", "passwordHash", "token", "accessToken",
                        "refreshToken", "authorization", "secret", "apiKey"));
    }
    @Override public String format(byte[] body, String contentType) {
        if (body == null || body.length == 0) return "<empty>";
        if (body.length > maxInputBytes) return "<body omitted: " + body.length + " bytes exceeds log input limit>";
        try {
            String media = contentType == null ? "" : contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
            String formatted;
            if (media.equals("application/json") || media.endsWith("+json")) {
                JsonNode tree = mapper.readTree(body);
                if (tree == null) return "<empty JSON>";
                redact(tree);
                formatted = mapper.writeValueAsString(tree);
            } else if (media.startsWith("text/")) {
                Charset charset = StandardCharsets.UTF_8; // explicit default for this log formatter
                Matcher match = CHARSET.matcher(contentType);
                if (match.find()) charset = Charset.forName(match.group(1));
                formatted = new String(body, charset);
            } else {
                return "<binary or unknown content: " + body.length + " bytes>";
            }
            // Keep an event on one line, and cap the final escaped output.
            formatted = formatted.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
            return formatted.length() <= maxOutputChars ? formatted :
                    formatted.substring(0, maxOutputChars) + "...<truncated>";
        } catch (Exception ignored) {
            return "<body omitted: parsing, charset, or redaction failed>";
        }
    }
    private void redact(JsonNode node) {
        if (node.isObject()) {
            ObjectNode object = (ObjectNode) node;
            List<String> names = new ArrayList<>();
            object.fieldNames().forEachRemaining(names::add);
            for (String name : names) {
                if (redactedFields.contains(name.toLowerCase(Locale.ROOT))) object.put(name, "[REDACTED]");
                else redact(object.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(this::redact);
        }
    }
}
