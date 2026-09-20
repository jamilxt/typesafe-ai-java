package ai.typesafe.exception;

import java.util.Map;

/** The rate limit was exceeded (429). Exposes the server-requested wait. */
public class TypeSafeRateLimitException extends TypeSafeAPIException {
    private final Long retryAfterMs;

    public TypeSafeRateLimitException(String body, Map<String, String> headers) {
        super(429, body, headers);
        this.retryAfterMs = parseRetryAfterMs(headers);
    }

    /** The server's requested wait in milliseconds, or null if unavailable. */
    public Long retryAfterMs() {
        return retryAfterMs;
    }

    static Long parseRetryAfterMs(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        String ms = headers.get("retry-after-ms");
        if (ms != null) {
            try {
                return Long.parseLong(ms.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        String seconds = headers.get("retry-after");
        if (seconds != null) {
            try {
                return (long) (Double.parseDouble(seconds.trim()) * 1000.0);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
