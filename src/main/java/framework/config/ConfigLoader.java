package framework.config;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import framework.config.AuthenticationConfig.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

/** Loads and validates a consumer-owned YAML catalog into one immutable suite snapshot. */
public final class ConfigLoader {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);
    private static final Pattern NAME = Pattern.compile("[a-z][a-z0-9-]*");
    private static final Set<String> ROOT = Set.of("defaults", "environments", "authentication", "services");
    private static final Set<String> DEFAULTS = Set.of("http", "logging");
    private static final Set<String> HTTP = Set.of("timeoutSeconds");
    private static final Set<String> LOGGING = Set.of("bodies");
    private static final Set<String> ENVIRONMENT = Set.of("allowDestructiveTests", "logging");
    private static final Set<String> AUTH = Set.of("tokenProviders", "profiles");
    private static final Set<String> PROVIDER = Set.of("type", "service", "username", "passwordCredential",
            "loginPath", "refreshPath", "clientId", "clientSecretCredential", "tokenPath", "scopes", "tokenCredential");
    private static final Set<String> PROFILE = Set.of("type", "tokenProvider", "credential", "placement", "name", "members");
    private static final Set<String> SERVICE = Set.of("timeoutSeconds", "defaultAuthentication", "environments");
    private static final Set<String> SERVICE_ENVIRONMENT = Set.of("baseUrl");
    private final ObjectMapper mapper;

    /**
     * Creates a data-only YAML parser with duplicate keys, aliases, excessive
     * nesting, and trailing content rejected before domain validation begins.
     */
    public ConfigLoader() {
        LoaderOptions limits = new LoaderOptions();
        limits.setAllowDuplicateKeys(false);
        limits.setMaxAliasesForCollections(0);
        limits.setNestingDepthLimit(20);
        limits.setCodePointLimit(1_000_000);
        YAMLFactory factory = YAMLFactory.builder().loaderOptions(limits)
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
        mapper = new ObjectMapper(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    /**
     * Loads, validates, merges, and resolves a catalog for one environment.
     * The returned object is a complete immutable suite snapshot; consumers do
     * not read YAML or override sources themselves.
     */
    public FrameworkConfig loadFromClasspath(String resourceName, String selectedEnvironment) {
        String resource = text(resourceName, "Configuration resource");
        String environment = name(selectedEnvironment, "Environment");
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        InputStream input = context == null ? null : context.getResourceAsStream(resource);
        if (input == null) input = ConfigLoader.class.getClassLoader().getResourceAsStream(resource);
        if (input == null) throw new ConfigException("Configuration resource not found on classpath: " + resource);
        try (InputStream owned = input) {
            JsonNode root = mapper.readTree(owned);
            if (root == null) throw new ConfigException("Configuration resource is empty: " + resource);
            validate(root);
            FrameworkConfig config = resolve(root, environment);
            LOG.info("Configuration loaded: environment={}, services={}, authProfiles={}, bodyLogging={}, bodyLogMaxChars={}, destructiveTests={}",
                    config.environment(), config.serviceNames(), config.authentication().profiles().keySet(), config.logBodies(),
                    config.maxBodyLogCharacters(), config.allowDestructiveTests());
            return config;
        } catch (ConfigException failure) {
            throw failure;
        } catch (IOException failure) {
            throw new ConfigException("Could not parse configuration resource: " + resource, failure);
        }
    }

    /** Validates structure and cross-references independently of the selected environment. */
    private void validate(JsonNode root) {
        object(root, "root"); unknown(root, "root", ROOT);
        JsonNode defaults = requiredObject(root, "defaults", "root.defaults"); unknown(defaults, "root.defaults", DEFAULTS);
        JsonNode http = requiredObject(defaults, "http", "root.defaults.http"); unknown(http, "root.defaults.http", HTTP);
        positiveLong(http, "timeoutSeconds", "root.defaults.http.timeoutSeconds");
        JsonNode logging = requiredObject(defaults, "logging", "root.defaults.logging"); unknown(logging, "root.defaults.logging", LOGGING);
        bool(logging, "bodies", "root.defaults.logging.bodies");

        JsonNode environments = requiredObject(root, "environments", "root.environments"); nonEmpty(environments, "root.environments");
        environments.properties().forEach(entry -> validateEnvironment(entry.getKey(), entry.getValue()));

        // Authentication is parsed first so service defaults can be checked
        // against known profiles during service validation.
        AuthenticationConfig authentication = parseAuthentication(requiredObject(root, "authentication", "root.authentication"));
        JsonNode services = requiredObject(root, "services", "root.services"); nonEmpty(services, "root.services");
        services.properties().forEach(entry -> validateService(entry.getKey(), entry.getValue(), authentication));

        // Password and OAuth providers name the service hosting their token
        // endpoints. Static-token providers make no HTTP call and need no service.
        for (TokenProvider provider : authentication.tokenProviders().values()) {
            String service = provider instanceof PasswordLoginProvider p ? p.service()
                    : provider instanceof OAuthClientCredentialsProvider p ? p.service() : null;
            if (service != null && !services.has(service)) {
                throw new ConfigException("Token provider '" + provider.name() + "' references unknown service '" + service + "'");
            }
        }
    }

    /** Validates one environment policy block without resolving service values. */
    private void validateEnvironment(String rawName, JsonNode node) {
        String path = "root.environments." + name(rawName, "Environment name");
        JsonNode value = object(node, path); unknown(value, path, ENVIRONMENT);
        bool(value, "allowDestructiveTests", path + ".allowDestructiveTests");
        if (value.has("logging")) {
            JsonNode logging = object(value.get("logging"), path + ".logging"); unknown(logging, path + ".logging", LOGGING);
            bool(logging, "bodies", path + ".logging.bodies");
        }
    }

    /** Converts the authentication YAML subtree into typed, immutable descriptions. */
    private AuthenticationConfig parseAuthentication(JsonNode node) {
        unknown(node, "root.authentication", AUTH);
        JsonNode providerNodes = requiredObject(node, "tokenProviders", "root.authentication.tokenProviders");
        JsonNode profileNodes = requiredObject(node, "profiles", "root.authentication.profiles");
        Map<String, TokenProvider> providers = new LinkedHashMap<>();
        providerNodes.properties().forEach(entry -> {
            String providerName = name(entry.getKey(), "Token provider name");
            String path = "root.authentication.tokenProviders." + providerName;
            JsonNode value = object(entry.getValue(), path); unknown(value, path, PROVIDER);
            String type = string(value, "type", path + ".type");
            // Reject fields belonging to another provider type. This catches
            // misspellings and prevents silently ignored security settings.
            unknown(value, path, switch (type) {
                case "password-login" -> Set.of("type", "service", "username", "passwordCredential", "loginPath", "refreshPath");
                case "oauth2-client-credentials" -> Set.of("type", "service", "clientId", "clientSecretCredential", "tokenPath", "scopes");
                case "static-token" -> Set.of("type", "tokenCredential");
                default -> PROVIDER;
            });
            TokenProvider provider = switch (type) {
                case "password-login" -> new PasswordLoginProvider(providerName,
                        string(value, "service", path + ".service"), string(value, "username", path + ".username"),
                        string(value, "passwordCredential", path + ".passwordCredential"),
                        string(value, "loginPath", path + ".loginPath"), string(value, "refreshPath", path + ".refreshPath"));
                case "oauth2-client-credentials" -> new OAuthClientCredentialsProvider(providerName,
                        string(value, "service", path + ".service"), string(value, "clientId", path + ".clientId"),
                        string(value, "clientSecretCredential", path + ".clientSecretCredential"),
                        string(value, "tokenPath", path + ".tokenPath"), list(value, "scopes", path + ".scopes", true));
                case "static-token" -> new StaticTokenProvider(providerName,
                        string(value, "tokenCredential", path + ".tokenCredential"));
                default -> throw new ConfigException("Unsupported token-provider type at " + path + ".type: " + type);
            };
            providers.put(providerName, provider);
        });

        Map<String, Profile> profiles = new LinkedHashMap<>();
        profileNodes.properties().forEach(entry -> {
            String profileName = name(entry.getKey(), "Authentication profile name");
            String path = "root.authentication.profiles." + profileName;
            JsonNode value = object(entry.getValue(), path); unknown(value, path, PROFILE);
            String type = string(value, "type", path + ".type");
            // Profile types also have strict, non-overlapping field sets.
            unknown(value, path, switch (type) {
                case "bearer" -> Set.of("type", "tokenProvider");
                case "api-key" -> Set.of("type", "credential", "placement", "name");
                case "composite" -> Set.of("type", "members");
                default -> PROFILE;
            });
            Profile profile = switch (type) {
                case "bearer" -> new BearerProfile(profileName, string(value, "tokenProvider", path + ".tokenProvider"));
                case "api-key" -> new ApiKeyProfile(profileName, string(value, "credential", path + ".credential"),
                        placement(string(value, "placement", path + ".placement"), path), string(value, "name", path + ".name"));
                case "composite" -> new CompositeProfile(profileName, list(value, "members", path + ".members", false));
                default -> throw new ConfigException("Unsupported authentication-profile type at " + path + ".type: " + type);
            };
            profiles.put(profileName, profile);
        });
        AuthenticationConfig result = new AuthenticationConfig(providers, profiles);
        validateReferences(result);
        return result;
    }

    /** Validates every provider/profile reference before runtime objects are constructed. */
    private void validateReferences(AuthenticationConfig authentication) {
        for (Profile profile : authentication.profiles().values()) {
            if (profile instanceof BearerProfile bearer
                    && !authentication.tokenProviders().containsKey(bearer.tokenProvider())) {
                throw new ConfigException("Authentication profile '" + bearer.name()
                        + "' references unknown token provider '" + bearer.tokenProvider() + "'");
            }
            if (profile instanceof CompositeProfile composite) {
                Set<String> seen = new HashSet<>();
                for (String member : composite.members()) {
                    if (!authentication.profiles().containsKey(member)) throw new ConfigException("Composite profile '"
                            + composite.name() + "' references unknown profile '" + member + "'");
                    if (!seen.add(member)) throw new ConfigException("Composite profile '" + composite.name()
                            + "' contains duplicate member '" + member + "'");
                }
            }
        }
        // Duplicate members are handled above; this depth-first traversal finds
        // direct and indirect composite cycles such as A -> B -> A.
        Set<String> visited = new HashSet<>();
        for (String profile : authentication.profiles().keySet()) cycle(profile, authentication, new HashSet<>(), visited);
    }

    /** Performs depth-first cycle detection over composite-profile references. */
    private void cycle(String current, AuthenticationConfig authentication, Set<String> visiting, Set<String> visited) {
        if (visited.contains(current)) return;
        if (!visiting.add(current)) throw new ConfigException("Cyclic composite authentication profile involving '" + current + "'");
        Profile profile = authentication.profile(current);
        if (profile instanceof CompositeProfile composite) {
            for (String member : composite.members()) cycle(member, authentication, visiting, visited);
        }
        visiting.remove(current); visited.add(current);
    }

    /** Validates one service definition and its default-profile reference. */
    private void validateService(String rawName, JsonNode node, AuthenticationConfig authentication) {
        String serviceName = name(rawName, "Service name");
        String path = "root.services." + serviceName;
        JsonNode service = object(node, path); unknown(service, path, SERVICE);
        if (service.has("timeoutSeconds")) positiveLong(service, "timeoutSeconds", path + ".timeoutSeconds");
        if (service.has("defaultAuthentication")) {
            String profile = string(service, "defaultAuthentication", path + ".defaultAuthentication");
            if (!authentication.profiles().containsKey(profile)) throw new ConfigException(path
                    + ".defaultAuthentication references unknown profile '" + profile + "'");
        }
        JsonNode environments = requiredObject(service, "environments", path + ".environments"); nonEmpty(environments, path + ".environments");
        environments.properties().forEach(entry -> {
            String envPath = path + ".environments." + name(entry.getKey(), "Environment name");
            JsonNode environment = object(entry.getValue(), envPath); unknown(environment, envPath, SERVICE_ENVIRONMENT);
            string(environment, "baseUrl", envPath + ".baseUrl");
        });
    }

    /** Resolves the selected environment and runtime overrides into typed values. */
    private FrameworkConfig resolve(JsonNode root, String environmentName) {
        JsonNode defaults = root.get("defaults");
        JsonNode environment = namedObject(root.get("environments"), environmentName, "environment");
        long timeout = overrideLong("framework.http.timeout-seconds",
                positiveLong(defaults.get("http"), "timeoutSeconds", "root.defaults.http.timeoutSeconds"));
        boolean bodiesDefault = bool(defaults.get("logging"), "bodies", "root.defaults.logging.bodies");
        boolean bodiesForEnvironment = environment.has("logging")
                ? bool(environment.get("logging"), "bodies", "environment logging.bodies") : bodiesDefault;
        boolean logBodies = overrideBoolean("framework.logging.bodies", bodiesForEnvironment);
        int maxBodyCharacters = overrideInt("framework.logging.max-body-chars", 8000);
        boolean destructive = overrideBoolean("framework.execution.allow-destructive-tests",
                bool(environment, "allowDestructiveTests", "environment allowDestructiveTests"));
        if ("prod".equals(environmentName) && destructive) throw new ConfigException("Production configuration must disable destructive tests");

        AuthenticationConfig authentication = parseAuthentication(root.get("authentication"));
        Map<String, ServiceConfig> services = new LinkedHashMap<>();
        root.get("services").properties().forEach(entry -> {
            String serviceName = entry.getKey(); JsonNode service = entry.getValue();
            JsonNode serviceEnvironment = namedObject(service.get("environments"), environmentName,
                    "environment for service '" + serviceName + "'");
            long serviceTimeout = service.has("timeoutSeconds")
                    ? positiveLong(service, "timeoutSeconds", "service timeoutSeconds") : timeout;
            serviceTimeout = overrideLong("services." + serviceName + ".timeout-seconds", serviceTimeout);
            String baseUrl = override("services." + serviceName + ".base-url", string(serviceEnvironment, "baseUrl", "service baseUrl"));
            String defaultProfile = service.has("defaultAuthentication")
                    ? string(service, "defaultAuthentication", "service defaultAuthentication") : null;
            services.put(serviceName, new ServiceConfig(serviceName, uri(baseUrl, "services." + serviceName + ".base-url"),
                    Duration.ofSeconds(serviceTimeout), defaultProfile));
        });
        return new FrameworkConfig(environmentName, Duration.ofSeconds(timeout), logBodies, maxBodyCharacters,
                destructive, authentication, services);
    }

    /** Applies override precedence: Java system property, OS environment, then YAML. */
    private String override(String property, String yamlValue) {
        String value = System.getProperty(property);
        if (value != null) return text(value, "System property " + property).trim();
        String environment = property.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
        value = System.getenv(environment);
        return value == null ? yamlValue : text(value, "Environment variable " + environment).trim();
    }
    /** Resolves and strictly parses a positive long override. */
    private long overrideLong(String property, long value) {
        try { long result = Long.parseLong(override(property, Long.toString(value))); if (result < 1) throw new NumberFormatException(); return result; }
        catch (NumberFormatException failure) { throw new ConfigException(property + " must be a positive whole number", failure); }
    }
    /** Resolves a positive integer override and rejects overflow. */
    private int overrideInt(String property, int value) {
        long result = overrideLong(property, value); if (result > Integer.MAX_VALUE) throw new ConfigException(property + " is too large"); return (int) result;
    }
    /** Resolves a boolean override, accepting only explicit true or false text. */
    private boolean overrideBoolean(String property, boolean value) {
        String result = override(property, Boolean.toString(value));
        if ("true".equalsIgnoreCase(result)) return true; if ("false".equalsIgnoreCase(result)) return false;
        throw new ConfigException(property + " must be exactly true or false");
    }

    /** Converts the YAML placement text into the closed credential-placement enum. */
    private static CredentialPlacement placement(String value, String path) {
        return switch (value.toLowerCase(Locale.ROOT)) { case "header" -> CredentialPlacement.HEADER;
            case "query" -> CredentialPlacement.QUERY; default -> throw new ConfigException(path + ".placement must be header or query"); };
    }
    /** Reads a YAML string list and optionally enforces at least one value. */
    private static List<String> list(JsonNode parent, String field, String path, boolean allowEmpty) {
        JsonNode node = parent.get(field); if (node == null || !node.isArray()) throw new ConfigException(path + " must be a YAML list");
        List<String> result = new ArrayList<>();
        for (JsonNode value : node) { if (!value.isTextual()) throw new ConfigException(path + " values must be strings"); result.add(text(value.textValue(), path + " value")); }
        if (!allowEmpty && result.isEmpty()) throw new ConfigException(path + " must not be empty"); return result;
    }
    /** Parses a URI while translating library failures into configuration errors. */
    private static URI uri(String value, String path) { try { return URI.create(value); } catch (IllegalArgumentException e) { throw new ConfigException(path + " is not a valid URI", e); } }

    /** Finds a required named child object and reports available names when absent. */
    private static JsonNode namedObject(JsonNode parent, String name, String description) {
        JsonNode result = parent.get(name); if (result == null) throw new ConfigException("Missing " + description + " '" + name + "'. Available values: " + fieldNames(parent));
        return object(result, description + " '" + name + "'");
    }
    /** Reads a required field and verifies that its value is a YAML object. */
    private static JsonNode requiredObject(JsonNode parent, String field, String path) { JsonNode value = parent.get(field); if (value == null) throw new ConfigException("Missing required object: " + path); return object(value, path); }

    /** Verifies that a node exists and has YAML object shape. */
    private static JsonNode object(JsonNode value, String path) { if (value == null || !value.isObject()) throw new ConfigException(path + " must be a YAML object"); return value; }

    /** Rejects empty catalogs whose presence alone would otherwise pass validation. */
    private static void nonEmpty(JsonNode value, String path) { if (value.isEmpty()) throw new ConfigException(path + " must not be empty"); }

    /** Reads, trims, and validates a required YAML string field. */
    private static String string(JsonNode parent, String field, String path) { JsonNode value = parent.get(field); if (value == null || !value.isTextual()) throw new ConfigException(path + " must be a YAML string"); return text(value.textValue(), path).trim(); }

    /** Reads a required YAML boolean without coercing string values. */
    private static boolean bool(JsonNode parent, String field, String path) { JsonNode value = parent.get(field); if (value == null || !value.isBoolean()) throw new ConfigException(path + " must be the boolean true or false"); return value.booleanValue(); }

    /** Reads a required positive integral value that fits in a Java long. */
    private static long positiveLong(JsonNode parent, String field, String path) { JsonNode value = parent.get(field); if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 1) throw new ConfigException(path + " must be a positive whole number"); return value.longValue(); }

    /** Rejects unknown keys so misspelled settings cannot be silently ignored. */
    private static void unknown(JsonNode value, String path, Set<String> allowed) { value.fieldNames().forEachRemaining(key -> { if (!allowed.contains(key)) throw new ConfigException("Unknown configuration property " + path + "." + key + ". Allowed properties: " + allowed); }); }

    /** Normalizes a logical catalog name and enforces its portable naming pattern. */
    private static String name(String value, String label) { String result = text(value, label).trim().toLowerCase(Locale.ROOT); if (!NAME.matcher(result).matches()) throw new ConfigException(label + " must match " + NAME.pattern() + ": " + value); return result; }

    /** Shared null/blank guard for configuration text. */
    private static String text(String value, String label) { if (value == null || value.isBlank()) throw new ConfigException(label + " must not be blank"); return value; }

    /** Formats object keys for missing-environment and missing-service diagnostics. */
    private static String fieldNames(JsonNode object) { StringBuilder result = new StringBuilder("["); Iterator<String> names = object.fieldNames(); while (names.hasNext()) { if (result.length() > 1) result.append(", "); result.append(names.next()); } return result.append(']').toString(); }
}
