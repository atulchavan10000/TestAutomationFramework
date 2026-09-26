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
    private final Map<String, ServiceConfig> services;

    public FrameworkConfig(
            String environment,
            Duration defaultHttpTimeout,
            boolean logBodies,
            int maxBodyLogCharacters,
            boolean allowDestructiveTests,
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

    public String environment() {
        return environment;
    }

    public Duration defaultHttpTimeout() {
        return defaultHttpTimeout;
    }

    public boolean logBodies() {
        return logBodies;
    }

    /** Maximum characters retained from a formatted request or response body. */
    public int maxBodyLogCharacters() {
        return maxBodyLogCharacters;
    }

    public boolean allowDestructiveTests() {
        return allowDestructiveTests;
    }

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

    private static String requireText(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
