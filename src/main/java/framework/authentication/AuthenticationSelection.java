package framework.authentication;

import java.util.Objects;
import java.util.Optional;

/**
 * Explicit per-request authentication choice.
 *
 * <p>{@link Kind#INHERIT} permits operation and service defaults, while
 * {@link Kind#DISABLED} deliberately suppresses them. {@link Kind#PROFILE}
 * selects one named profile and has the highest precedence.</p>
 */
public final class AuthenticationSelection {
    /** The three possible meanings of a request's authentication setting. */
    public enum Kind { INHERIT, DISABLED, PROFILE }
    private static final AuthenticationSelection INHERIT = new AuthenticationSelection(Kind.INHERIT, null);
    private static final AuthenticationSelection DISABLED = new AuthenticationSelection(Kind.DISABLED, null);
    private final Kind kind;
    private final String profile;

    /** Creates a validated internal representation used by the named factories below. */
    private AuthenticationSelection(Kind kind, String profile) { this.kind = kind; this.profile = profile; }
    /** Uses the operation profile, then the service default, when available. */
    public static AuthenticationSelection inherit() { return INHERIT; }

    /** Sends the request without authentication even when defaults exist. */
    public static AuthenticationSelection disabled() { return DISABLED; }

    /** Selects a specific named profile for this request. */
    public static AuthenticationSelection profile(String name) {
        Objects.requireNonNull(name, "Authentication profile must not be null");
        if (name.isBlank()) throw new IllegalArgumentException("Authentication profile must not be blank");
        return new AuthenticationSelection(Kind.PROFILE, name);
    }
    /** Returns how the runtime should interpret this selection. */
    public Kind kind() { return kind; }

    /** Returns the selected name only when {@link #kind()} is {@link Kind#PROFILE}. */
    public Optional<String> profile() { return Optional.ofNullable(profile); }
}
