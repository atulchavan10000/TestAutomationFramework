package framework.client;
import framework.http.HttpResponse;
import java.util.Objects;
/** A response was received but could not be mapped. Raw evidence is never discarded. */
public final class ResponseMappingException extends RuntimeException {
    private final HttpResponse rawResponse;
    public ResponseMappingException(String message, HttpResponse rawResponse, Throwable cause) {
        super(message, cause);
        this.rawResponse = Objects.requireNonNull(rawResponse);
    }
    public HttpResponse rawResponse() { return rawResponse; }
}
