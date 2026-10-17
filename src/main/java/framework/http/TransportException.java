package framework.http;
/** No complete usable HTTP response was obtained. This is not an HTTP error status. */
public final class TransportException extends RuntimeException {
    /** Wraps the library or network failure that prevented a response. */
    public TransportException(String message, Throwable cause) { super(message, cause); }
}
