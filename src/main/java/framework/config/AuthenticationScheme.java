package framework.config;

import java.util.Locale;

/**
 * Identifies how a service obtains authentication.
 *
 * This first configuration slice intentionally stores only the stable scheme.
 * Scheme-specific credentials, token endpoints, refresh rules, and header
 * behavior will be modeled when authentication architecture is designed.
 */
public enum AuthenticationScheme {
    NONE("none"),
    PASSWORD_LOGIN("password-login");

    private final String yamlValue;

    AuthenticationScheme(String yamlValue) {
        this.yamlValue = yamlValue;
    }

    /** Returns the lower-case spelling accepted in the consumer YAML catalog. */
    public String yamlValue() {
        return yamlValue;
    }

    /**
     * Converts a YAML value to a known scheme and rejects silent fallbacks.
     * Adding another scheme later requires an explicit enum value and parser case.
     */
    public static AuthenticationScheme fromYaml(String value) {
        if (value == null || value.isBlank()) {
            throw new ConfigException("Authentication scheme must not be blank");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (AuthenticationScheme scheme : values()) {
            if (scheme.yamlValue.equals(normalized)) {
                return scheme;
            }
        }

        throw new ConfigException("Unsupported authentication scheme: " + value);
    }
}
