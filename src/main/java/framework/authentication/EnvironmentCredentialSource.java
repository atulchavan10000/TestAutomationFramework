package framework.authentication;

import framework.config.ConfigException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads secrets from {@code TAF_SECRET_*} environment variables, including
 * values injected by Jenkins Credentials Binding. A logical reference such as
 * {@code checkout-user-password} maps to
 * {@code TAF_SECRET_CHECKOUT_USER_PASSWORD}.
 */
public final class EnvironmentCredentialSource implements CredentialSource {
    private static final Pattern REFERENCE = Pattern.compile("[a-z][a-z0-9-]*");
    private final Map<String, String> environment;
    /** Captures the process environment as an immutable snapshot. */
    public EnvironmentCredentialSource() { this(System.getenv()); }

    /** Package-private map injection allows deterministic tests without changing process state. */
    EnvironmentCredentialSource(Map<String, String> environment) { this.environment = Map.copyOf(environment); }

    /** Resolves a required logical reference using the documented environment-name convention. */
    @Override public String required(String reference) {
        Objects.requireNonNull(reference, "Credential reference must not be null");
        if (!REFERENCE.matcher(reference).matches()) {
            throw new ConfigException("Credential reference must match " + REFERENCE.pattern());
        }
        // Keep secret names portable and predictable across local shells and CI.
        String variable = "TAF_SECRET_" + reference.replace('-', '_').toUpperCase(Locale.ROOT);
        String value = environment.get(variable);
        if (value == null || value.isBlank()) {
            // Name the reference and variable so setup can be fixed, but never include a secret value.
            throw new ConfigException("Missing credential '" + reference + "' (environment variable " + variable + ")");
        }
        return value;
    }
}
