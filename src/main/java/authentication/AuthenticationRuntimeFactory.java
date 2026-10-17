package authentication;

import authentication.model.LoginResponse;
import authentication.model.OAuthTokenResponse;
import framework.authentication.*;
import framework.client.ApiClientFactory;
import framework.config.AuthenticationConfig;
import framework.config.AuthenticationConfig.*;
import framework.config.ConfigException;
import framework.config.FrameworkConfig;
import framework.context.TestContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Consumer wiring that turns validated authentication configuration into one
 * suite-scoped runtime object graph.
 *
 * <p>The reusable framework owns provider/authenticator contracts, while this
 * consumer-side factory knows the product's concrete login API and response
 * DTOs. Secrets are resolved here, never stored in {@link FrameworkConfig}.</p>
 */
public final class AuthenticationRuntimeFactory {
    /** Utility class; runtime construction is exposed only through {@link #create}. */
    private AuthenticationRuntimeFactory() {}

    /**
     * Builds every named token provider first and then every request profile.
     * This order lets bearer profiles reference already-created provider caches.
     */
    public static AuthenticationRuntime create(FrameworkConfig config, CredentialSource credentials) {
        Map<String, AccessTokenProvider> tokenProviders = new LinkedHashMap<>();
        for (TokenProvider provider : config.authentication().tokenProviders().values()) {
            tokenProviders.put(provider.name(), createTokenProvider(config, credentials, provider));
        }

        Map<String, RequestAuthenticator> profiles = new LinkedHashMap<>();
        for (String name : config.authentication().profiles().keySet()) {
            buildProfile(name, config.authentication(), credentials, tokenProviders, profiles);
        }
        return new AuthenticationRuntime(profiles);
    }

    /**
     * Translates one validated provider description into its executable token
     * provider. Static tokens are returned directly; login and OAuth providers
     * are wrapped in independent suite-scoped caches.
     */
    private static AccessTokenProvider createTokenProvider(
            FrameworkConfig config, CredentialSource credentials, TokenProvider provider) {
        if (provider instanceof StaticTokenProvider value) {
            return new StaticAccessTokenProvider(credentials.required(value.tokenCredential()));
        }
        if (provider instanceof PasswordLoginProvider value) {
            // Resolve once during suite setup. The configuration retains only
            // the logical reference and never receives the actual password.
            String password = credentials.required(value.passwordCredential());
            return new CachingAccessTokenProvider(new TokenAcquirer() {
                @Override public TokenGrant acquire(TestContext context) {
                    LoginResponse response = api(config, value.service(), context)
                            .login(value.loginPath(), value.username(), password).body();
                    return loginGrant(response);
                }
                @Override public boolean supportsRefresh() { return true; }
                @Override public TokenGrant refresh(String refreshToken, TestContext context) {
                    return loginGrant(api(config, value.service(), context)
                            .refresh(value.refreshPath(), refreshToken).body());
                }
            });
        }
        if (provider instanceof OAuthClientCredentialsProvider value) {
            String secret = credentials.required(value.clientSecretCredential());
            // Client-credentials grants normally have no refresh token, so the
            // cache invokes this acquisition strategy again near expiry.
            return new CachingAccessTokenProvider(context -> {
                OAuthTokenResponse response = api(config, value.service(), context)
                        .clientCredentials(value.tokenPath(), value.clientId(), secret, value.scopes()).body();
                return new TokenGrant(response.accessToken(), null, Duration.ofSeconds(response.expiresIn()));
            });
        }
        throw new ConfigException("Unsupported token provider '" + provider.name() + "'");
    }

    /**
     * Materializes one named request profile, recursively building composite
     * members and memoizing completed authenticators in {@code built}.
     */
    private static RequestAuthenticator buildProfile(String name, AuthenticationConfig config,
            CredentialSource credentials, Map<String, AccessTokenProvider> tokenProviders,
            Map<String, RequestAuthenticator> built) {
        RequestAuthenticator existing = built.get(name);
        // Composite profiles may refer to a member already built directly or
        // by another composite. Reuse the same authenticator instance.
        if (existing != null) return existing;
        Profile profile = config.profile(name);
        RequestAuthenticator result;
        if (profile instanceof BearerProfile bearer) {
            result = new BearerAuthenticator(tokenProviders.get(bearer.tokenProvider()));
        } else if (profile instanceof ApiKeyProfile apiKey) {
            result = new ApiKeyAuthenticator(credentials.required(apiKey.credential()),
                    apiKey.placement(), apiKey.parameterName());
        } else if (profile instanceof CompositeProfile composite) {
            List<RequestAuthenticator> members = new ArrayList<>();
            // Reference validation and cycle detection happened in ConfigLoader;
            // recursion here only materializes the validated object graph.
            for (String member : composite.members()) {
                members.add(buildProfile(member, config, credentials, tokenProviders, built));
            }
            result = new CompositeAuthenticator(members);
        } else {
            throw new ConfigException("Unsupported authentication profile '" + name + "'");
        }
        built.put(name, result);
        return result;
    }

    /**
     * Creates the consumer authentication API for a token service without an
     * authentication runtime, preventing login requests from authenticating recursively.
     */
    private static AuthApi api(FrameworkConfig config, String service, TestContext context) {
        // Passing no AuthenticationRuntime prevents token requests from authenticating recursively.
        return new AuthApi(ApiClientFactory.forTest(config, service, context, null));
    }

    /** Converts the consumer login DTO into the framework's protocol-neutral grant. */
    private static TokenGrant loginGrant(LoginResponse response) {
        return new TokenGrant(response.accessToken(), response.refreshToken(), Duration.ofSeconds(response.expiresIn()));
    }
}
