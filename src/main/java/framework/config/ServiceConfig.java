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
    // null means this service sends no authentication unless an operation/request selects a profile.
    private final String defaultAuthenticationProfile;

    /** Creates one environment-resolved service configuration. */
    public ServiceConfig(
            String name,
            URI baseUri,
            Duration timeout,
            String defaultAuthenticationProfile) {

        this.name = requireText(name, "Service name");
        this.baseUri = validateBaseUri(baseUri);
        this.timeout = Objects.requireNonNull(timeout, "Service timeout must not be null");
        this.defaultAuthenticationProfile = defaultAuthenticationProfile == null
                ? null : requireText(defaultAuthenticationProfile, "Default authentication profile");

        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Service timeout must be positive");
        }
    }

    /** Returns the logical service-catalog name. */
    public String name() {
        return name;
    }

    /** Returns the selected environment's validated HTTP base URI. */
    public URI baseUri() {
        return baseUri;
    }

    /** Returns the effective timeout after defaults and overrides are applied. */
    public Duration timeout() {
        return timeout;
    }

    /** Returns the service default profile, or empty when authentication is opt-in. */
    public java.util.Optional<String> defaultAuthenticationProfile() {
        return java.util.Optional.ofNullable(defaultAuthenticationProfile);
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

    /** Shared constructor guard for required service names and profile references. */
    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
