package framework.config;

/**
 * Reports an invalid catalog, environment selection, or runtime override.
 *
 * Configuration errors stop the suite during setup. Continuing with guessed
 * defaults could send requests to the wrong environment, which is less safe
 * than failing before the first HTTP call.
 */
public final class ConfigException extends RuntimeException {
    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
