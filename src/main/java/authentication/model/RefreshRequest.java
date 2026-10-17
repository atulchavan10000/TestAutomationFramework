package authentication.model;

/** Request accepted by the custom refresh-token endpoint. */
public record RefreshRequest(String refreshToken) {}
