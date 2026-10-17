package framework.config;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable configuration snapshot for one complete TestNG suite execution.
 *
 * The loader finishes all merging and validation before constructing this object.
 * Consumers therefore receive typed values and never need to inspect YAML nodes,
 * environment variables, system properties, or unvalidated strings.
 */
public final class FrameworkConfig {
    private final String environment;
    private final Duration defaultHttpTimeout;
    private final boolean logBodies;
    private final int maxBodyLogCharacters;
    private final boolean allowDestructiveTests;
    private final AuthenticationConfig authentication;
    private final Map<String, ServiceConfig> services;

    /** Creates the final validated configuration snapshot for one selected environment. */
    public FrameworkConfig(
            String environment,
            Duration defaultHttpTimeout,
            boolean logBodies,
            int maxBodyLogCharacters,
            boolean allowDestructiveTests,
            AuthenticationConfig authentication,
            Map<String, ServiceConfig> services) {

        this.environment = requireText(environment, "Environment");
        this.defaultHttpTimeout = Objects.requireNonNull(
                defaultHttpTimeout, "Default HTTP timeout must not be null");
        if (defaultHttpTimeout.isZero() || defaultHttpTimeout.isNegative()) {
            throw new IllegalArgumentException("Default HTTP timeout must be positive");
        }

        this.logBodies = logBodies;
        if (maxBodyLogCharacters < 1) {
            throw new IllegalArgumentException("Maximum body log characters must be positive");
        }
        this.maxBodyLogCharacters = maxBodyLogCharacters;
        this.allowDestructiveTests = allowDestructiveTests;
        this.authentication = Objects.requireNonNull(authentication, "Authentication config must not be null");

        Objects.requireNonNull(services, "Services must not be null");
        if (services.isEmpty()) {
            throw new IllegalArgumentException("At least one service must be configured");
        }

        // LinkedHashMap preserves catalog order in diagnostics. The unmodifiable
        // wrapper prevents consumers from changing the suite's configuration snapshot.
        Map<String, ServiceConfig> copy = new LinkedHashMap<>();
        services.forEach((name, service) -> copy.put(
                requireText(name, "Service map key"),
                Objects.requireNonNull(service, "Service config must not be null")));
        this.services = Collections.unmodifiableMap(copy);
    }

    /** Returns the normalized environment selected for this suite. */
    public String environment() {
        return environment;
    }

    /** Returns the default timeout inherited by services without an override. */
    public Duration defaultHttpTimeout() {
        return defaultHttpTimeout;
    }

    /** Returns whether sanitized request and response bodies may be logged. */
    public boolean logBodies() {
        return logBodies;
    }

    /** Maximum characters retained from a formatted request or response body. */
    public int maxBodyLogCharacters() {
        return maxBodyLogCharacters;
    }

    /** Returns the environment policy enforced by the destructive-test listener. */
    public boolean allowDestructiveTests() {
        return allowDestructiveTests;
    }

    /** Returns provider/profile descriptions without resolved secret values. */
    public AuthenticationConfig authentication() { return authentication; }

    /** Returns all configured names without exposing the mutable map used by loading. */
    public Set<String> serviceNames() {
        return services.keySet();
    }

    /**
     * Returns a required service or fails with the available names. A missing
     * service is a setup error, not something an API test should recover from.
     */
    public ServiceConfig service(String name) {
        String requiredName = requireText(name, "Service name");
        ServiceConfig service = services.get(requiredName);
        if (service == null) {
            throw new ConfigException(
                    "Service '" + requiredName + "' is not configured. Available services: " + services.keySet());
        }
        return service;
    }

    /** Shared constructor/accessor guard for required logical names. */
    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
