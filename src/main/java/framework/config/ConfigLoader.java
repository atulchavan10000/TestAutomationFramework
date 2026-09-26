package framework.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads a consumer-owned YAML catalog and creates one typed configuration snapshot.
 *
 * This class knows the catalog schema and merge rules. It does not know TestNG,
 * Gradle, Jenkins, or any product-specific service names. The caller supplies
 * the catalog resource and the environment selected by the test lifecycle.
 */
public final class ConfigLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    // Environment and service names become map keys and runtime-override prefixes.
    // Restricting their alphabet also prevents path-like input such as "../prod".
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");

    private static final Set<String> ROOT_KEYS = Set.of("defaults", "environments", "services");
    private static final Set<String> DEFAULT_KEYS = Set.of("http", "logging");
    private static final Set<String> HTTP_KEYS = Set.of("timeoutSeconds");
    private static final Set<String> LOGGING_KEYS = Set.of("bodies");
    private static final Set<String> ENVIRONMENT_KEYS = Set.of("allowDestructiveTests", "logging");
    private static final Set<String> SERVICE_KEYS = Set.of("timeoutSeconds", "auth", "environments");
    private static final Set<String> AUTH_KEYS = Set.of("scheme");
    private static final Set<String> SERVICE_ENVIRONMENT_KEYS = Set.of("baseUrl");

    private final ObjectMapper mapper;

    public ConfigLoader() {
        LoaderOptions yamlLimits = new LoaderOptions();
        yamlLimits.setAllowDuplicateKeys(false);
        yamlLimits.setMaxAliasesForCollections(0);
        yamlLimits.setNestingDepthLimit(20);
        yamlLimits.setCodePointLimit(1_000_000);

        // Parse YAML into a neutral tree. We never ask YAML to instantiate a class
        // named by the document, so custom YAML tags cannot construct arbitrary objects.
        YAMLFactory yamlFactory = YAMLFactory.builder()
                .loaderOptions(yamlLimits)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .build();
        this.mapper = new ObjectMapper(yamlFactory)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * Loads a YAML resource from the consumer project's runtime classpath.
     * For this project the resource is "config/config.yaml" under src/test/resources.
     */
    public FrameworkConfig loadFromClasspath(String resourceName, String selectedEnvironment) {
        String resource = requireText(resourceName, "Configuration resource");
        String environment = normalizeName(selectedEnvironment, "Environment");

        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader fallbackLoader = ConfigLoader.class.getClassLoader();
        InputStream input = contextLoader == null ? null : contextLoader.getResourceAsStream(resource);
        if (input == null && fallbackLoader != contextLoader) {
            input = fallbackLoader.getResourceAsStream(resource);
        }
        if (input == null) {
            throw new ConfigException("Configuration resource not found on classpath: " + resource);
        }

        try (InputStream ownedInput = input) {
            JsonNode root = mapper.readTree(ownedInput);
            if (root == null) {
                throw new ConfigException("Configuration resource is empty: " + resource);
            }
            validateCatalog(root);
            FrameworkConfig config = resolve(root, environment);
            LOG.info(
                    "Configuration loaded: environment={}, services={}, bodyLogging={}, destructiveTests={}",
                    config.environment(), config.serviceNames(), config.logBodies(),
                    config.allowDestructiveTests());
            return config;
        } catch (ConfigException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new ConfigException("Could not parse configuration resource: " + resource, failure);
        }
    }

    /** Validates the complete document, not only the currently selected environment. */
    private void validateCatalog(JsonNode root) {
        requireObject(root, "root");
        rejectUnknownKeys(root, "root", ROOT_KEYS);

        JsonNode defaults = requiredObject(root, "defaults", "root.defaults");
        rejectUnknownKeys(defaults, "root.defaults", DEFAULT_KEYS);
        JsonNode http = requiredObject(defaults, "http", "root.defaults.http");
        rejectUnknownKeys(http, "root.defaults.http", HTTP_KEYS);
        positiveLong(http, "timeoutSeconds", "root.defaults.http.timeoutSeconds");
        JsonNode logging = requiredObject(defaults, "logging", "root.defaults.logging");
        rejectUnknownKeys(logging, "root.defaults.logging", LOGGING_KEYS);
        requiredBoolean(logging, "bodies", "root.defaults.logging.bodies");

        JsonNode environments = requiredObject(root, "environments", "root.environments");
        requireNonEmpty(environments, "root.environments");
        environments.properties().forEach(entry -> {
            String name = normalizeName(entry.getKey(), "Environment name");
            JsonNode environment = requireObject(entry.getValue(), "root.environments." + name);
            rejectUnknownKeys(environment, "root.environments." + name, ENVIRONMENT_KEYS);
            requiredBoolean(environment, "allowDestructiveTests",
                    "root.environments." + name + ".allowDestructiveTests");
            if (environment.has("logging")) {
                JsonNode envLogging = requiredObject(environment, "logging",
                        "root.environments." + name + ".logging");
                rejectUnknownKeys(envLogging, "root.environments." + name + ".logging", LOGGING_KEYS);
                requiredBoolean(envLogging, "bodies",
                        "root.environments." + name + ".logging.bodies");
            }
        });

        JsonNode services = requiredObject(root, "services", "root.services");
        requireNonEmpty(services, "root.services");
        services.properties().forEach(entry -> validateService(entry.getKey(), entry.getValue()));
    }

    private void validateService(String rawName, JsonNode service) {
        String name = normalizeName(rawName, "Service name");
        String path = "root.services." + name;
        requireObject(service, path);
        rejectUnknownKeys(service, path, SERVICE_KEYS);

        if (service.has("timeoutSeconds")) {
            positiveLong(service, "timeoutSeconds", path + ".timeoutSeconds");
        }

        JsonNode auth = requiredObject(service, "auth", path + ".auth");
        rejectUnknownKeys(auth, path + ".auth", AUTH_KEYS);
        AuthenticationScheme.fromYaml(requiredText(auth, "scheme", path + ".auth.scheme"));

        JsonNode environments = requiredObject(service, "environments", path + ".environments");
        requireNonEmpty(environments, path + ".environments");
        environments.properties().forEach(entry -> {
            String environmentName = normalizeName(entry.getKey(), "Environment name");
            String environmentPath = path + ".environments." + environmentName;
            JsonNode environment = requireObject(entry.getValue(), environmentPath);
            rejectUnknownKeys(environment, environmentPath, SERVICE_ENVIRONMENT_KEYS);
            requiredText(environment, "baseUrl", environmentPath + ".baseUrl");
        });
    }

    /** Applies defaults, environment values, then external runtime overrides. */
    private FrameworkConfig resolve(JsonNode root, String environmentName) {
        JsonNode defaults = root.get("defaults");
        JsonNode environment = requiredNamedObject(
                root.get("environments"), environmentName, "environment");

        long defaultTimeoutSeconds = overriddenPositiveLong(
                "framework.http.timeout-seconds",
                positiveLong(defaults.get("http"), "timeoutSeconds",
                        "root.defaults.http.timeoutSeconds"));

        boolean defaultBodies = requiredBoolean(
                defaults.get("logging"), "bodies", "root.defaults.logging.bodies");
        boolean environmentBodies = environment.has("logging")
                ? requiredBoolean(environment.get("logging"), "bodies",
                        "root.environments." + environmentName + ".logging.bodies")
                : defaultBodies;
        boolean logBodies = overriddenBoolean("framework.logging.bodies", environmentBodies);

        boolean yamlDestructive = requiredBoolean(
                environment, "allowDestructiveTests",
                "root.environments." + environmentName + ".allowDestructiveTests");
        boolean allowDestructive = overriddenBoolean(
                "framework.execution.allow-destructive-tests", yamlDestructive);

        // Production safety is a hard invariant. A runtime override may make a
        // configuration stricter, but it cannot enable destructive or body logging.
        if ("prod".equals(environmentName) && (allowDestructive || logBodies)) {
            throw new ConfigException(
                    "Production configuration must disable destructive tests and body logging");
        }

        Map<String, ServiceConfig> resolvedServices = new LinkedHashMap<>();
        root.get("services").properties().forEach(entry -> {
            String serviceName = entry.getKey();
            JsonNode service = entry.getValue();
            JsonNode serviceEnvironment = requiredNamedObject(
                    service.get("environments"), environmentName,
                    "environment for service '" + serviceName + "'");

            long yamlTimeout = service.has("timeoutSeconds")
                    ? positiveLong(service, "timeoutSeconds",
                            "root.services." + serviceName + ".timeoutSeconds")
                    : defaultTimeoutSeconds;
            long timeoutSeconds = overriddenPositiveLong(
                    "services." + serviceName + ".timeout-seconds", yamlTimeout);

            String yamlBaseUrl = requiredText(
                    serviceEnvironment, "baseUrl",
                    "root.services." + serviceName + ".environments."
                            + environmentName + ".baseUrl");
            String baseUrl = runtimeOverride("services." + serviceName + ".base-url", yamlBaseUrl);

            AuthenticationScheme scheme = AuthenticationScheme.fromYaml(
                    requiredText(service.get("auth"), "scheme",
                            "root.services." + serviceName + ".auth.scheme"));

            resolvedServices.put(serviceName, new ServiceConfig(
                    serviceName,
                    parseUri(baseUrl, "services." + serviceName + ".base-url"),
                    Duration.ofSeconds(timeoutSeconds),
                    scheme));
        });

        return new FrameworkConfig(
                environmentName,
                Duration.ofSeconds(defaultTimeoutSeconds),
                logBodies,
                allowDestructive,
                resolvedServices);
    }

    /** System properties intentionally take precedence over OS environment values. */
    private String runtimeOverride(String propertyName, String yamlValue) {
        String systemValue = System.getProperty(propertyName);
        if (systemValue != null) {
            return requireText(systemValue, "System property " + propertyName).trim();
        }

        String environmentName = propertyName
                .replaceAll("[^A-Za-z0-9]", "_")
                .toUpperCase(Locale.ROOT);
        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null) {
            return requireText(environmentValue, "Environment variable " + environmentName).trim();
        }
        return yamlValue;
    }

    private long overriddenPositiveLong(String propertyName, long yamlValue) {
        String text = runtimeOverride(propertyName, Long.toString(yamlValue));
        try {
            long parsed = Long.parseLong(text);
            if (parsed < 1) {
                throw new NumberFormatException("value is not positive");
            }
            return parsed;
        } catch (NumberFormatException failure) {
            throw new ConfigException(propertyName + " must be a positive whole number", failure);
        }
    }

    private boolean overriddenBoolean(String propertyName, boolean yamlValue) {
        String text = runtimeOverride(propertyName, Boolean.toString(yamlValue));
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        throw new ConfigException(propertyName + " must be exactly true or false");
    }

    private static URI parseUri(String value, String path) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException failure) {
            throw new ConfigException(path + " is not a valid URI", failure);
        }
    }

    private static JsonNode requiredNamedObject(JsonNode parent, String name, String description) {
        JsonNode result = parent.get(name);
        if (result == null) {
            throw new ConfigException(
                    "Missing " + description + " '" + name + "'. Available values: " + fieldNames(parent));
        }
        return requireObject(result, description + " '" + name + "'");
    }

    private static JsonNode requiredObject(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null) {
            throw new ConfigException("Missing required object: " + path);
        }
        return requireObject(value, path);
    }

    private static JsonNode requireObject(JsonNode node, String path) {
        if (!node.isObject()) {
            throw new ConfigException(path + " must be a YAML object");
        }
        return node;
    }

    private static void requireNonEmpty(JsonNode object, String path) {
        if (object.isEmpty()) {
            throw new ConfigException(path + " must not be empty");
        }
    }

    private static String requiredText(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual()) {
            throw new ConfigException(path + " must be a YAML string");
        }
        return requireText(value.textValue(), path).trim();
    }

    private static boolean requiredBoolean(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw new ConfigException(path + " must be the boolean true or false");
        }
        return value.booleanValue();
    }

    private static long positiveLong(JsonNode parent, String field, String path) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()
                || value.longValue() < 1) {
            throw new ConfigException(path + " must be a positive whole number");
        }
        return value.longValue();
    }

    private static void rejectUnknownKeys(JsonNode object, String path, Set<String> allowed) {
        object.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                throw new ConfigException(
                        "Unknown configuration property " + path + "." + name
                                + ". Allowed properties: " + allowed);
            }
        });
    }

    private static String normalizeName(String value, String label) {
        String normalized = requireText(value, label).trim().toLowerCase(Locale.ROOT);
        if (!NAME.matcher(normalized).matches()) {
            throw new ConfigException(
                    label + " must match " + NAME.pattern() + ": " + value);
        }
        return normalized;
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ConfigException(label + " must not be blank");
        }
        return value;
    }

    private static String fieldNames(JsonNode object) {
        StringBuilder names = new StringBuilder("[");
        Iterator<String> iterator = object.fieldNames();
        while (iterator.hasNext()) {
            if (names.length() > 1) {
                names.append(", ");
            }
            names.append(iterator.next());
        }
        return names.append(']').toString();
    }
}
