package framework.authentication;

import framework.context.TestContext;
import java.util.Objects;

/**
 * Returns a configured static token unchanged. It performs no acquisition,
 * expiry calculation, decoding, or refresh; external secret rotation is visible
 * only when a new runtime is constructed.
 */
public final class StaticAccessTokenProvider implements AccessTokenProvider {
    private final String token;

    /** Stores one validated secret value for the lifetime of this provider. */
    public StaticAccessTokenProvider(String token) {
        Objects.requireNonNull(token, "Static token must not be null");
        if (token.isBlank()) throw new IllegalArgumentException("Static token must not be blank");
        this.token = token;
    }
    /** Returns the suite-scoped token after validating that a test context was supplied. */
    @Override public String accessToken(TestContext context) { Objects.requireNonNull(context); return token; }
}
