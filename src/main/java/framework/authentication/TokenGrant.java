package framework.authentication;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Validated result of token acquisition. The optional refresh token stays
 * inside authentication infrastructure, and {@code expiresIn} is a relative
 * lifetime measured from the acquisition time observed by the cache.
 */
public record TokenGrant(String accessToken, String refreshToken, Duration expiresIn) {
    public TokenGrant {
        accessToken = required(accessToken, "Access token");
        if (refreshToken != null && refreshToken.isBlank()) throw new IllegalArgumentException("Refresh token must not be blank");
        Objects.requireNonNull(expiresIn, "Token lifetime must not be null");
        if (expiresIn.isZero() || expiresIn.isNegative()) throw new IllegalArgumentException("Token lifetime must be positive");
    }
    /** Avoids exposing nullable refresh-token handling to cache logic. */
    public Optional<String> optionalRefreshToken() { return Optional.ofNullable(refreshToken); }
    /** Validates required token text without ever including its value in an error. */
    private static String required(String value, String label) {
        Objects.requireNonNull(value, label + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(label + " must not be blank");
        return value;
    }
}
