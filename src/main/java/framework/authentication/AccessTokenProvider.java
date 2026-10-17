package framework.authentication;

import framework.context.TestContext;

/**
 * Supplies an opaque access token to a bearer authenticator.
 *
 * <p>An implementation may return a configured static token or acquire and
 * cache tokens from an authentication service. Callers deliberately do not
 * decode JWT claims; expiry and refresh are provider responsibilities.</p>
 */
public interface AccessTokenProvider {

    /**
     * Returns a token that is usable for the current request.
     *
     * @param context test-scoped context propagated to any token HTTP call
     * @return the opaque token value, without a {@code Bearer } prefix
     */
    String accessToken(TestContext context);
}
