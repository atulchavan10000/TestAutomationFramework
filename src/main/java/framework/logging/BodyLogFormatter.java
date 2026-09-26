package framework.logging;
/** Formats a copy of captured content for logs; must not change execution evidence. */
public interface BodyLogFormatter {
    String format(byte[] body, String contentType);
}
