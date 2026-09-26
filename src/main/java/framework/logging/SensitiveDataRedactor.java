package framework.logging;

import framework.http.HttpHeaders;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Produces log-safe URI and header text while keeping ordinary values visible.
 *
 * Redaction is name based and case insensitive. For example, {@code page=2}
 * remains visible while {@code token=abc} becomes {@code token=[REDACTED]}.
 * The request itself is never changed; only the string written to the log is.
 */
public final class SensitiveDataRedactor {
    private static final String REDACTED = "[REDACTED]";

    private static final Set<String> DEFAULT_SENSITIVE_NAMES = Set.of(
            "authorization",
            "proxyauthorization",
            "cookie",
            "setcookie",
            "password",
            "passwordhash",
            "token",
            "accesstoken",
            "refreshtoken",
            "secret",
            "clientsecret",
            "apikey",
            "xapikey");

    private final Set<String> sensitiveNames;

    public SensitiveDataRedactor(Set<String> sensitiveNames) {
        if (sensitiveNames == null) {
            throw new NullPointerException("Sensitive names must not be null");
        }
        this.sensitiveNames = sensitiveNames.stream()
                .map(SensitiveDataRedactor::normalizeName)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Uses the framework's conservative list of common credential names. */
    public static SensitiveDataRedactor defaults() {
        return new SensitiveDataRedactor(DEFAULT_SENSITIVE_NAMES);
    }

    /** Exposes the immutable names so JSON body redaction can use the same policy. */
    public Set<String> sensitiveNames() {
        return sensitiveNames;
    }

    /**
     * Formats the full request URI without ever printing URI user information.
     * Every non-sensitive query parameter remains visible in its encoded form.
     */
    public String uri(URI uri) {
        StringBuilder result = new StringBuilder()
                .append(uri.getScheme())
                .append("://");

        String host = uri.getHost();
        // URI#getHost omits brackets around IPv6 literals, so restore them for a
        // readable and unambiguous logged URI.
        if (host != null && host.contains(":")) {
            result.append('[').append(host).append(']');
        } else {
            result.append(host);
        }
        if (uri.getPort() >= 0) {
            result.append(':').append(uri.getPort());
        }

        String path = uri.getRawPath();
        result.append(path == null || path.isEmpty() ? "/" : singleLine(path));

        String query = uri.getRawQuery();
        if (query != null) {
            result.append('?').append(redactQuery(query));
        }
        return result.toString();
    }

    /** Formats all request or response headers, redacting only sensitive names. */
    public String headers(HttpHeaders headers) {
        StringBuilder result = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<String>> entry : headers.asMap().entrySet()) {
            if (!first) {
                result.append(", ");
            }
            first = false;
            result.append(singleLine(entry.getKey())).append('=');
            if (isSensitiveName(entry.getKey())) {
                result.append(REDACTED);
            } else {
                result.append('[');
                for (int index = 0; index < entry.getValue().size(); index++) {
                    if (index > 0) {
                        result.append(", ");
                    }
                    result.append(singleLine(entry.getValue().get(index)));
                }
                result.append(']');
            }
        }
        return result.append('}').toString();
    }

    private String redactQuery(String rawQuery) {
        String[] parameters = rawQuery.split("&", -1);
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < parameters.length; index++) {
            if (index > 0) {
                result.append('&');
            }

            String parameter = parameters[index];
            int equals = parameter.indexOf('=');
            String rawName = equals < 0 ? parameter : parameter.substring(0, equals);
            if (isSensitiveName(decodeQueryName(rawName))) {
                result.append(singleLine(rawName)).append('=').append(REDACTED);
            } else {
                result.append(singleLine(parameter));
            }
        }
        return result.toString();
    }

    /** Returns whether a field, query, or header name belongs to the sensitive set. */
    public boolean isSensitiveName(String name) {
        return sensitiveNames.contains(normalizeName(name));
    }

    private static String decodeQueryName(String rawName) {
        try {
            return URLDecoder.decode(rawName, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedEncoding) {
            // A malformed name cannot be decoded, but normalization of the raw
            // name still catches ordinary spellings such as "access_token".
            return rawName;
        }
    }

    /** Treat access-token, access_token and accessToken as the same name. */
    private static String normalizeName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder normalized = new StringBuilder(lower.length());
        for (int index = 0; index < lower.length(); index++) {
            char character = lower.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                normalized.append(character);
            }
        }
        return normalized.toString();
    }

    private static String singleLine(String value) {
        return value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t");
    }
}
