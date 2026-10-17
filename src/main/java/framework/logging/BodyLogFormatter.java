package framework.logging;
/** Formats a copy of captured content for logs; it must not change execution evidence. */
public interface BodyLogFormatter {

    /**
     * Produces a bounded, safe log representation of captured body bytes.
     *
     * @param body defensive copy of request or response content
     * @param contentType declared media type, possibly blank
     * @return text suitable for diagnostics, with configured fields redacted
     */
    String format(byte[] body, String contentType);
}
