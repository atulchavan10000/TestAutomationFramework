package framework.authentication;

import framework.context.TestContext;
import framework.http.HttpRequest;
import java.util.Objects;

/**
 * Adds an RFC-style bearer Authorization header using an
 * {@link AccessTokenProvider}. A caller-supplied Authorization header is
 * preserved so authentication-specific tests can deliberately send malformed
 * or invalid credentials.
 */
public final class BearerAuthenticator implements RequestAuthenticator {
    private final AccessTokenProvider provider;

    /** Creates a bearer strategy backed by the selected token provider. */
    public BearerAuthenticator(AccessTokenProvider provider) {
        this.provider = Objects.requireNonNull(provider, "Access-token provider must not be null");
    }
    @Override public HttpRequest authenticate(HttpRequest request, TestContext context) {
        // Explicit request values have higher precedence than framework defaults.
        if (request.headers().contains("Authorization")) return request;
        return request.toBuilder().headers(request.headers().toBuilder()
                .set("Authorization", "Bearer " + provider.accessToken(context)).build()).build();
    }
}
