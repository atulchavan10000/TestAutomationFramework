package framework.authentication;

/**
 * Resolves logical credential references without exposing the backing store to
 * authentication providers. Implementations may read environment variables or
 * integrate with an external secret manager.
 */
public interface CredentialSource {

    /**
     * Resolves one required secret and fails without including its value in the error.
     *
     * @param reference non-secret logical name stored in configuration
     * @return resolved secret value
     */
    String required(String reference);
}
