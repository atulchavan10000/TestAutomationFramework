package framework.authentication;

import framework.context.TestContext;

/**
 * Performs protocol-specific token acquisition for a caching provider.
 * Cache policy and concurrency remain in {@link CachingAccessTokenProvider};
 * this strategy only knows how to communicate with the token service.
 */
public interface TokenAcquirer {

    /** Obtains a new token grant without relying on a previous grant. */
    TokenGrant acquire(TestContext context);

    /** Indicates whether {@link #refresh(String, TestContext)} is implemented. */
    default boolean supportsRefresh() { return false; }

    /**
     * Exchanges a refresh token for a new grant.
     *
     * @throws UnsupportedOperationException when this acquisition protocol has no refresh flow
     */
    default TokenGrant refresh(String refreshToken, TestContext context) {
        throw new UnsupportedOperationException("This token provider does not support refresh");
    }
}
