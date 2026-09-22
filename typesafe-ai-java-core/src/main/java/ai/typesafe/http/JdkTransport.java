package ai.typesafe.http;

import ai.typesafe.exception.TypeSafeAPIConnectionException;
import ai.typesafe.exception.TypeSafeAPITimeoutException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Default transport over the JDK {@link HttpClient}. No external dependency.
 */
public final class JdkTransport implements Transport {

    private static final String USER_AGENT = "typesafe-ai-java/" + SdkVersion.version();

    private final HttpClient client;

    public JdkTransport() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
    }

    public JdkTransport(HttpClient client) {
        this.client = client;
    }

    @Override
    public Response postJson(String url, String apiKey, String jsonBody, double timeoutSeconds) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis((long) (timeoutSeconds * 1000.0)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, String> headers = new HashMap<>();
            response.headers().map().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    headers.put(k.toLowerCase(), v.get(0));
                }
            });
            return new Response(response.statusCode(), Map.copyOf(headers), response.body());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new TypeSafeAPITimeoutException("Request timed out after " + timeoutSeconds + "s", e);
        } catch (java.io.IOException e) {
            throw new TypeSafeAPIConnectionException("Request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeAPIConnectionException("Request interrupted", e);
        }
    }

    @Override
    public Response getJson(String url, String apiKey, double timeoutSeconds) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis((long) (timeoutSeconds * 1000.0)))
                .header("Authorization", "Bearer " + apiKey)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        try {
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            Map<String, String> headers = new HashMap<>();
            response.headers().map().forEach((k, v) -> {
                if (!v.isEmpty()) {
                    headers.put(k.toLowerCase(), v.get(0));
                }
            });
            return new Response(response.statusCode(), Map.copyOf(headers), response.body());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new TypeSafeAPITimeoutException("Request timed out after " + timeoutSeconds + "s", e);
        } catch (java.io.IOException e) {
            throw new TypeSafeAPIConnectionException("Request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeAPIConnectionException("Request interrupted", e);
        }
    }
}
