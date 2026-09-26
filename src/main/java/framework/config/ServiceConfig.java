package framework.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Immutable effective configuration for one service in one selected environment.
 * Stable endpoint paths do not belong here; consumer-owned API classes keep them.
 */
public final class ServiceConfig {
    private final String name;
    private final URI baseUri;
    private final Duration timeout;
    private final AuthenticationScheme authenticationScheme;

    public ServiceConfig(
            String name,
            URI baseUri,
            Duration timeout,
            AuthenticationScheme authenticationScheme) {

        this.name = requireText(name, "Service name");
        this.baseUri = validateBaseUri(baseUri);
        this.timeout = Objects.requireNonNull(timeout, "Service timeout must not be null");
        this.authenticationScheme = Objects.requireNonNull(
                authenticationScheme, "Authentication scheme must not be null");

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Service timeout must be positive");
        }
    }

    public String name() {
        return name;
    }

    public URI baseUri() {
        return baseUri;
    }

    public Duration timeout() {
        return timeout;
    }

    public AuthenticationScheme authenticationScheme() {
        return authenticationScheme;
    }

    /**
     * Accept only a clean HTTP origin/base path. Queries, fragments, embedded
     * credentials, and relative URIs would make later request resolution ambiguous.
     */
    private static URI validateBaseUri(URI uri) {
        Objects.requireNonNull(uri, "Service base URI must not be null");
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                || uri.getHost() == null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || uri.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Service base URI must be HTTP(S), with a host and no query, fragment, or credentials");
        }
        return uri;
    }

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
