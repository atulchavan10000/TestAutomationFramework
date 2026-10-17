package framework.authentication;

import framework.config.ConfigException;
import framework.context.TestContext;
import framework.http.HttpRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Suite-scoped registry of executable authentication profiles.
 *
 * <p>Selection is deterministic: a request override wins, followed by the
 * operation profile and then the service default. No selected profile means
 * that the request is sent without authentication. A rejected response never
 * causes this registry to try a different profile.</p>
 */
public final class AuthenticationRuntime {
    private final Map<String, RequestAuthenticator> profiles;

    /** Takes an immutable snapshot of the suite's executable named profiles. */
    public AuthenticationRuntime(Map<String, RequestAuthenticator> profiles) {
        Objects.requireNonNull(profiles, "Authentication profiles must not be null");
        this.profiles = Map.copyOf(new LinkedHashMap<>(profiles));
    }

    /** Resolves the effective profile and applies it once to the request. */
    public HttpRequest authenticate(HttpRequest request, AuthenticationSelection selection,
            String operationProfile, String serviceDefaultProfile, TestContext context) {
        Objects.requireNonNull(request, "HTTP request must not be null");
        Objects.requireNonNull(selection, "Authentication selection must not be null");

        // DISABLED is an explicit negative-test choice, so it must not fall
        // through to either the operation or service default.
        if (selection.kind() == AuthenticationSelection.Kind.DISABLED) return request;

        // PROFILE is the explicit request override. INHERIT follows the stable
        // operation profile first and the broader service default second.
        String selected = selection.kind() == AuthenticationSelection.Kind.PROFILE
                ? selection.profile().orElseThrow()
                : operationProfile != null ? operationProfile : serviceDefaultProfile;
        if (selected == null) return request;
        RequestAuthenticator authenticator = profiles.get(selected);
        if (authenticator == null) throw new ConfigException("Unknown authentication profile '" + selected + "'");
        return authenticator.authenticate(request, context);
    }
}
