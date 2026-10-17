package framework.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable authentication catalog for one suite.
 *
 * The catalog contains only provider settings and logical credential references.
 * It deliberately never contains resolved passwords, client secrets, API keys,
 * or static tokens.
 */
public final class AuthenticationConfig {
    private final Map<String, TokenProvider> tokenProviders;
    private final Map<String, Profile> profiles;

    /** Creates defensive, insertion-ordered copies of both named catalogs. */
    public AuthenticationConfig(Map<String, TokenProvider> tokenProviders, Map<String, Profile> profiles) {
        this.tokenProviders = immutableMap(tokenProviders, "Token providers");
        this.profiles = immutableMap(profiles, "Authentication profiles");
    }

    /** Returns immutable descriptions of the supported token-acquisition strategies. */
    public Map<String, TokenProvider> tokenProviders() { return tokenProviders; }

    /** Returns immutable descriptions of mechanisms applied to outgoing requests. */
    public Map<String, Profile> profiles() { return profiles; }

    /** Returns a required provider description or reports a configuration error. */
    public TokenProvider tokenProvider(String name) {
        TokenProvider provider = tokenProviders.get(name);
        if (provider == null) {
            throw new ConfigException("Unknown token provider '" + name + "'");
        }
        return provider;
    }

    /** Returns a required request profile or reports a configuration error. */
    public Profile profile(String name) {
        Profile profile = profiles.get(name);
        if (profile == null) {
            throw new ConfigException("Unknown authentication profile '" + name + "'");
        }
        return profile;
    }

    /**
     * Closed set of supported ways to obtain bearer tokens. These records are
     * configuration descriptions; runtime behavior implements
     * {@code framework.authentication.AccessTokenProvider}.
     */
    public sealed interface TokenProvider permits PasswordLoginProvider, OAuthClientCredentialsProvider,
            StaticTokenProvider {
        String name();
    }

    /** Obtains access and refresh tokens from the application's username/password endpoints. */
    public record PasswordLoginProvider(
            String name,
            String service,
            String username,
            String passwordCredential,
            String loginPath,
            String refreshPath) implements TokenProvider {
        public PasswordLoginProvider {
            name = text(name, "Provider name");
            service = text(service, "Token service");
            username = text(username, "Username");
            passwordCredential = text(passwordCredential, "Password credential reference");
            loginPath = path(loginPath, "Login path");
            refreshPath = path(refreshPath, "Refresh path");
        }
    }

    /** Obtains a scoped token using the OAuth 2.0 client-credentials grant. */
    public record OAuthClientCredentialsProvider(
            String name,
            String service,
            String clientId,
            String clientSecretCredential,
            String tokenPath,
            List<String> scopes) implements TokenProvider {
        public OAuthClientCredentialsProvider {
            name = text(name, "Provider name");
            service = text(service, "Token service");
            clientId = text(clientId, "OAuth client ID");
            clientSecretCredential = text(clientSecretCredential, "Client-secret credential reference");
            tokenPath = path(tokenPath, "OAuth token path");
            scopes = immutableTextList(scopes, "OAuth scopes");
        }
    }

    /** Reads an already-issued bearer token from a credential source; it never refreshes it. */
    public record StaticTokenProvider(String name, String tokenCredential) implements TokenProvider {
        public StaticTokenProvider {
            name = text(name, "Provider name");
            tokenCredential = text(tokenCredential, "Static-token credential reference");
        }
    }

    /**
     * Closed set of mechanisms that can modify an outgoing request. Profiles
     * refer to providers and other profiles by validated logical names.
     */
    public sealed interface Profile permits BearerProfile, ApiKeyProfile, CompositeProfile {
        String name();
    }

    /** Attaches a token supplied by the referenced provider as a bearer header. */
    public record BearerProfile(String name, String tokenProvider) implements Profile {
        public BearerProfile {
            name = text(name, "Profile name");
            tokenProvider = text(tokenProvider, "Token-provider reference");
        }
    }

    /** Supported locations for API-key credentials. */
    public enum CredentialPlacement { HEADER, QUERY }

    /** Attaches a secret API key using the configured header or query name. */
    public record ApiKeyProfile(
            String name,
            String credential,
            CredentialPlacement placement,
            String parameterName) implements Profile {
        public ApiKeyProfile {
            name = text(name, "Profile name");
            credential = text(credential, "API-key credential reference");
            placement = Objects.requireNonNull(placement, "API-key placement must not be null");
            parameterName = text(parameterName, "API-key header/query name");
        }
    }

    /** Applies every named member in order; members are requirements, not alternatives. */
    public record CompositeProfile(String name, List<String> members) implements Profile {
        public CompositeProfile {
            name = text(name, "Profile name");
            members = immutableTextList(members, "Composite members");
            if (members.isEmpty()) {
                throw new IllegalArgumentException("Composite profile must contain at least one member");
            }
        }
    }

    /** Creates a null-safe, insertion-ordered, unmodifiable catalog copy. */
    private static <T> Map<String, T> immutableMap(Map<String, T> source, String label) {
        Objects.requireNonNull(source, label + " must not be null");
        Map<String, T> copy = new LinkedHashMap<>();
        source.forEach((name, value) -> copy.put(text(name, label + " key"),
                Objects.requireNonNull(value, label + " value must not be null")));
        return Collections.unmodifiableMap(copy);
    }

    /** Validates every list element and prevents later mutation of configuration. */
    private static List<String> immutableTextList(List<String> source, String label) {
        Objects.requireNonNull(source, label + " must not be null");
        List<String> copy = new ArrayList<>();
        source.forEach(value -> copy.add(text(value, label + " value")));
        return Collections.unmodifiableList(copy);
    }

    /** Validates a service-relative absolute path such as {@code /api/v1/token}. */
    private static String path(String value, String label) {
        String result = text(value, label);
        if (!result.startsWith("/")) throw new IllegalArgumentException(label + " must start with /");
        return result;
    }

    /** Rejects null or blank configuration text and otherwise preserves its value. */
    private static String text(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
