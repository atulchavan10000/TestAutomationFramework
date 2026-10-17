package framework.authentication;

import framework.context.TestContext;
import framework.http.HttpRequest;

/**
 * Applies one credential mechanism to an immutable prepared request.
 * Implementations return a modified copy and must preserve an explicit caller
 * credential when that mechanism supports negative-test overrides.
 */
public interface RequestAuthenticator {

    /**
     * Adds this authenticator's credentials to a prepared request.
     *
     * @param request immutable request produced by {@code ApiClient}
     * @param context test-scoped context for token calls and diagnostics
     * @return the original request when no change is needed, otherwise a modified copy
     */
    HttpRequest authenticate(HttpRequest request, TestContext context);
}
