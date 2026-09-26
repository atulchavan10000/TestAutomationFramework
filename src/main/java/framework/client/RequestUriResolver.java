package framework.client;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/**
 * Joins a configured base path with an operation path and encodes parameter DATA.
 * Base https://host/api + operation /users becomes https://host/api/users.
 * Operation templates contain literal path segments and {named} placeholders.
 * Query strings belong in RequestOptions. No absolute URL override is accepted.
 */
public final class RequestUriResolver {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^{}]+)}");
    private final URI base;

    public RequestUriResolver(URI base) {
        this.base = Objects.requireNonNull(base);
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getRawQuery() != null
                || base.getRawFragment() != null || base.getRawUserInfo() != null) {
            throw new IllegalArgumentException("Base URI must be HTTP(S), with host and no query, fragment, or credentials");
        }
    }

    public URI resolve(String path, RequestOptions options) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(options);
        if (!path.startsWith("/") || path.startsWith("//") || path.contains("?")
                || path.contains("#") || path.contains("\\")) {
            throw new IllegalArgumentException("Operation must be a /path template; supply queries through options");
        }
        Set<String> used = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(path);
        StringBuffer replaced = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = options.pathParameters().get(name);
            if (value == null) throw new IllegalArgumentException("Missing path parameter: " + name);
            used.add(name);
            // Dot-only values are escaped so data cannot become relative path navigation.
            String encoded = encode(value);
            if (value.equals(".") || value.equals("..")) encoded = value.replace(".", "%2E");
            matcher.appendReplacement(replaced, Matcher.quoteReplacement(encoded));
        }
        matcher.appendTail(replaced);
        if (!used.equals(options.pathParameters().keySet())) {
            throw new IllegalArgumentException("Unused path parameters");
        }
        String resolvedPath = replaced.toString();
        if (resolvedPath.contains("{") || resolvedPath.contains("}")) {
            throw new IllegalArgumentException("Unresolved or malformed path placeholder");
        }
        for (String segment : resolvedPath.split("/")) {
            if (segment.equals(".") || segment.equals("..")) throw new IllegalArgumentException("Relative path segments are unsupported");
        }
        String prefix = base.toASCIIString().replaceAll("/+$", "");
        StringJoiner query = new StringJoiner("&");
        options.queryParameters().forEach((name, values) ->
                values.forEach(value -> query.add(encode(name) + "=" + encode(value))));
        URI result = URI.create(prefix + resolvedPath + (query.length() == 0 ? "" : "?" + query));
        // The template's literal portions must already be legal URI path text.
        return URI.create(result.toASCIIString());
    }

    /** Percent-encode UTF-8 data using RFC 3986 unreserved characters, not form '+' encoding. */
    private static String encode(String value) {
        StringBuilder result = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~') {
                result.append((char)c);
            } else {
                result.append('%').append("0123456789ABCDEF".charAt(c >> 4))
                        .append("0123456789ABCDEF".charAt(c & 15));
            }
        }
        return result.toString();
    }
}
