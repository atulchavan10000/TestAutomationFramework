package authentication.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Standard client-credentials token response exposed by the local auth service. */
public record OAuthTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        String scope) {}
