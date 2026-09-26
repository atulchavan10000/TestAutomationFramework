package authentication.model;

/** Tokens returned by POST /api/v1/auth/login. */
public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn) {}
