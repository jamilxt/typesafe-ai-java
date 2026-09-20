package ai.typesafe.exception;

import java.util.Map;

/**
 * An unsuccessful HTTP response with its status, body, and headers.
 * Concrete subclasses exist for each documented status class.
 */
public class TypeSafeAPIException extends TypeSafeException {
    private final int status;
    private final String responseBody;
    private final Map<String, String> headers;

    public TypeSafeAPIException(int status, String responseBody, Map<String, String> headers) {
        super(buildMessage(status, responseBody, headers));
        this.status = status;
        this.responseBody = responseBody;
        this.headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    private static String buildMessage(int status, String body, Map<String, String> headers) {
        String requestId = headers == null ? null : headers.get("x-typesafe-request-id");
        String base = "TypeSafe API returned HTTP " + status;
        if (requestId != null) {
            base += " (request id: " + requestId + ")";
        }
        if (body != null && !body.isBlank()) {
            base += ": " + (body.length() > 512 ? body.substring(0, 512) + "..." : body);
        }
        return base;
    }

    public int status() {
        return status;
    }

    public String responseBody() {
        return responseBody;
    }

    /** Response headers (lower-cased names), immutable. */
    public Map<String, String> headers() {
        return headers;
    }

    /** The {@code x-typesafe-request-id} response header, or null. */
    public String requestId() {
        return headers.get("x-typesafe-request-id");
    }
}
