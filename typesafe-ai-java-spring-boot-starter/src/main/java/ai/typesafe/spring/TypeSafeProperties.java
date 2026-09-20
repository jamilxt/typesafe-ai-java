package ai.typesafe.spring;

import ai.typesafe.TypeSafeClient;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the TypeSafe AI client.
 *
 * <pre>
 * typesafe:
 *   api-key: ${TYPESAFE_API_KEY}
 *   base-url: https://api.typesafe.ai
 *   model: jev-latest
 *   timeout-seconds: 30
 *   max-retries: 2
 * </pre>
 */
@ConfigurationProperties(prefix = "typesafe")
public class TypeSafeProperties {

    /** API key; falls back to the TYPESAFE_API_KEY environment variable when blank. */
    private String apiKey = "";

    /** Endpoint override (e.g. a gateway URL). */
    private String baseUrl;

    /** Model used when a request does not pin one. */
    private String model = "jev-latest";

    /** Per-call timeout in seconds. */
    private double timeoutSeconds = 30.0;

    /** Maximum retries after the initial attempt. */
    private int maxRetries = 2;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public double getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(double timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
