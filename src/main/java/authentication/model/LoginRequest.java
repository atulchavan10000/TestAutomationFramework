package authentication.model;

/** Credentials accepted by auth-service. Keep actual values out of YAML and logs. */
public record LoginRequest(String username, String password) {}
