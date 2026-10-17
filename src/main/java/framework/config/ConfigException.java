package framework.config;

/**
 * Reports an invalid catalog, environment selection, or runtime override.
 *
 * Configuration errors stop the suite during setup. Continuing with guessed
 * defaults could send requests to the wrong environment, which is less safe
 * than failing before the first HTTP call.
 */
public final class ConfigException extends RuntimeException {
    /** Reports a validation or resolution failure without a lower-level cause. */
    public ConfigException(String message) {
        super(message);
    }

    /** Reports a configuration failure while retaining the parsing/conversion cause. */
    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
