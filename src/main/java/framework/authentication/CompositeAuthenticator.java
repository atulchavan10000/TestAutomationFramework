package framework.authentication;

import framework.context.TestContext;
import framework.http.HttpRequest;
import java.util.List;
import java.util.Objects;

/**
 * Applies every member in declaration order because every configured credential
 * is required. This is a composition mechanism, not a fallback that chooses the
 * first authenticator accepted by the server.
 */
public final class CompositeAuthenticator implements RequestAuthenticator {
    private final List<RequestAuthenticator> members;

    /** Freezes the ordered list of required member authenticators. */
    public CompositeAuthenticator(List<RequestAuthenticator> members) {
        this.members = List.copyOf(Objects.requireNonNull(members, "Composite members must not be null"));
        if (this.members.isEmpty()) throw new IllegalArgumentException("Composite authenticator must have members");
    }
    @Override public HttpRequest authenticate(HttpRequest request, TestContext context) {
        HttpRequest authenticated = request;
        // Each member receives the copy returned by the preceding member, so
        // bearer, API-key, and future mechanisms can accumulate credentials.
        for (RequestAuthenticator member : members) authenticated = member.authenticate(authenticated, context);
        return authenticated;
    }
}
