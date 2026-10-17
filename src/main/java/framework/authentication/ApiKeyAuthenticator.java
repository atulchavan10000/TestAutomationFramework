package framework.authentication;

import framework.config.AuthenticationConfig.CredentialPlacement;
import framework.context.TestContext;
import framework.http.HttpRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Adds an API key to its configured header or query parameter. An explicitly
 * supplied header or query parameter wins, which preserves deliberate invalid
 * credential values used by negative tests.
 */
public final class ApiKeyAuthenticator implements RequestAuthenticator {
    private final String value;
    private final CredentialPlacement placement;
    private final String parameterName;

    /** Creates an authenticator from an already-resolved key and placement policy. */
    public ApiKeyAuthenticator(String value, CredentialPlacement placement, String parameterName) {
        this.value = required(value, "API-key value");
        this.placement = Objects.requireNonNull(placement, "API-key placement must not be null");
        this.parameterName = required(parameterName, "API-key parameter name");
    }
    @Override public HttpRequest authenticate(HttpRequest request, TestContext context) {
        Objects.requireNonNull(context, "Test context must not be null");
        if (placement == CredentialPlacement.HEADER) {
            // Do not replace an explicit caller value, even when it is invalid.
            if (request.headers().contains(parameterName)) return request;
            return request.toBuilder().headers(request.headers().toBuilder().set(parameterName, value).build()).build();
        }
        return request.toBuilder().uri(appendQuery(request.uri())).build();
    }
    /** Adds the encoded key only when the configured query name is not already present. */
    private URI appendQuery(URI uri) {
        String name = encode(parameterName);
        String current = uri.getRawQuery();
        if (current != null) {
            for (String pair : current.split("&")) {
                // Compare the encoded parameter name without decoding or
                // rebuilding unrelated query values.
                if (pair.equals(name) || pair.startsWith(name + "=")) return uri;
            }
        }
        String suffix = (current == null || current.isEmpty() ? "?" : "&") + name + "=" + encode(value);
        // HttpRequest has already rejected fragments, so appending to its ASCII form is unambiguous
        // and preserves any percent-encoded path/query data without encoding it a second time.
        return URI.create(uri.toASCIIString() + suffix);
    }
    /** Encodes a query component without using form-style plus signs for spaces. */
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }

    /** Validates constructor text shared by the key value and parameter name. */
    private static String required(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank"); return value;
    }
}
