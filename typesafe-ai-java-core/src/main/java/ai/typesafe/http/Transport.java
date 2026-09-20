package ai.typesafe.http;

import java.util.Map;

/**
 * Minimal HTTP transport abstraction so tests can mock the wire and users
 * can plug a different client (e.g. behind a corporate proxy).
 */
public interface Transport {

    /** A raw HTTP response: status, lower-cased headers, and body. */
    record Response(int status, Map<String, String> headers, String body) {
    }

    /**
     * Executes a POST with a JSON body and Bearer auth.
     *
     * @param url          absolute endpoint URL
     * @param apiKey       bearer token
     * @param jsonBody     serialized request
     * @param timeoutSeconds per-call timeout in seconds
     * @return the response
     * @throws ai.typesafe.exception.TypeSafeAPITimeoutException on timeout
     * @throws ai.typesafe.exception.TypeSafeAPIConnectionException on connect/read failure
     */
    Response postJson(String url, String apiKey, String jsonBody, double timeoutSeconds);
}
