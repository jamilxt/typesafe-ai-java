package ai.typesafe;

import ai.typesafe.exception.TypeSafeAPIConnectionException;
import ai.typesafe.exception.TypeSafeAPIException;
import ai.typesafe.exception.TypeSafeAPITimeoutException;
import ai.typesafe.exception.TypeSafeAuthenticationException;
import ai.typesafe.exception.TypeSafeBadRequestException;
import ai.typesafe.exception.TypeSafeInternalServerException;
import ai.typesafe.exception.TypeSafeNotFoundException;
import ai.typesafe.exception.TypeSafePermissionDeniedException;
import ai.typesafe.exception.TypeSafeRateLimitException;
import ai.typesafe.exception.TypeSafeUnprocessableEntityException;
import ai.typesafe.http.JdkTransport;
import ai.typesafe.http.RetryPolicy;
import ai.typesafe.http.Transport;
import ai.typesafe.json.Json;
import ai.typesafe.model.EvaluationRequest;
import ai.typesafe.model.SystemOneResult;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client for the TypeSafe System One (Jev) evaluation API.
 *
 * <p>Threadsafety: instances are immutable and safe for concurrent use.</p>
 *
 * <pre>{@code
 * TypeSafeClient client = TypeSafeClient.fromEnv();
 * SystemOneResult result = client.evaluate(
 *     EvaluationRequest.of("Help! My payouts have been failing for 3 days.")
 *         .noul("is_urgent", "Does this convey urgency?")
 *         .choice("department", "Which team should handle this?",
 *             Map.of("billing", "Payments and refunds",
 *                    "technical", "Bugs and outages"))
 *         .build());
 *
 * if (result.noul("is_urgent").isYes(0.7)) { ... }
 * }</pre>
 */
public final class TypeSafeClient {

    /** The default endpoint. */
    public static final String DEFAULT_BASE_URL = "https://api.typesafe.ai";

    /** Environment variable read by {@link #fromEnv()}. */
    public static final String API_KEY_ENV = "TYPESAFE_API_KEY";

    /** The flagship model alias. */
    public static final String MODEL_JEV_LATEST = "jev-latest";

    private static final String EVALUATE_PATH = "/v1/systemone";
    private static final String MODELS_PATH = "/v1/models";

    private final String apiKey;
    private final String baseUrl;
    private final String defaultModel;
    private final RetryPolicy retryPolicy;
    private final Transport transport;
    private final double timeoutSeconds;

    private TypeSafeClient(Builder b) {
        this.apiKey = b.apiKey;
        this.baseUrl = b.baseUrl == null ? DEFAULT_BASE_URL : stripTrailingSlash(b.baseUrl);
        this.defaultModel = b.defaultModel;
        this.retryPolicy = b.retryPolicy;
        this.transport = b.transport;
        this.timeoutSeconds = b.timeoutSeconds;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /** Client reading the key from {@code TYPESAFE_API_KEY}. */
    public static TypeSafeClient fromEnv() {
        String key = System.getenv(API_KEY_ENV);
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(
                    "Environment variable " + API_KEY_ENV + " is not set; pass an api key explicitly instead");
        }
        return builder(key).build();
    }

    public static Builder builder(String apiKey) {
        return new Builder(apiKey);
    }

    /** Evaluates a request, applying the configured retry policy. */
    public SystemOneResult evaluate(EvaluationRequest request) {
        String model = request.model() != null ? request.model() : defaultModel;
        EvaluationRequest effective = model == null || model.equals(request.model())
                ? request
                : new EvaluationRequest(request.state(), model, request.questions());
        return executeWithRetries(Json.writeRequest(effective));
    }

    /**
     * Lists models available to the account.
     * Exposed as raw JSON for forward compatibility.
     */
    public String listModels() {
        return executeWithRetries(MODELS_PATH, "{}", response -> {
            if (response.status() >= 400) {
                throw apiException(response.status(), response.body(), response.headers());
            }
            return response.body();
        });
    }

    private SystemOneResult executeWithRetries(String body) {
        return executeWithRetries(EVALUATE_PATH, body, this::handleResponse);
    }

    private <T> T executeWithRetries(String path, String body, java.util.function.Function<Transport.Response, T> parse) {
        long deadline = retryPolicy.totalBudgetSeconds() == null
                ? Long.MAX_VALUE
                : System.nanoTime() + (long) (retryPolicy.totalBudgetSeconds() * 1_000_000_000L);
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                Transport.Response response =
                        transport.postJson(baseUrl + path, apiKey, body, timeoutSeconds);
                return parse.apply(response);
            } catch (TypeSafeAPIConnectionException | TypeSafeAPIException e) {
                Long retryAfterMs = null;
                if (e instanceof TypeSafeAPIException api) {
                    if (!isRetryableStatus(api.status())) {
                        throw e;
                    }
                    String msHeader = api.headers().get("retry-after-ms");
                    retryAfterMs = parseMillis(msHeader);
                    if (retryAfterMs == null) {
                        retryAfterMs = parseSeconds(api.headers().get("retry-after"));
                    }
                } else {
                    boolean isTimeout = e instanceof TypeSafeAPITimeoutException;
                    if (isTimeout ? !retryPolicy.retryTimeoutErrors() : !retryPolicy.retryConnectionErrors()) {
                        throw e;
                    }
                }
                if (attempt > retryPolicy.maxRetries()) {
                    throw e;
                }
                long delayMs = computeBackoffMs(attempt, retryAfterMs);
                if (delayMs > 0 && System.nanoTime() + delayMs * 1_000_000L >= deadline) {
                    throw e;
                }
                sleep(delayMs);
            }
        }
    }

    private boolean isRetryableStatus(int status) {
        return retryPolicy.httpStatuses().contains(status);
    }

    private Long parseSeconds(String v) {
        if (v == null) {
            return null;
        }
        try {
            return (long) (Double.parseDouble(v.trim()) * 1000.0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parses a {@code retry-after-ms} header; returns null when absent or malformed. */
    private static Long parseMillis(String v) {
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private long computeBackoffMs(int attempt, Long retryAfterMs) {
        if (retryPolicy.respectRetryAfter() && retryAfterMs != null) {
            return retryAfterMs;
        }
        double base = retryPolicy.backoffInitial().toMillis() * Math.pow(2, attempt - 1);
        double capped = Math.min(base, retryPolicy.backoffMax().toMillis());
        double jitter = capped * retryPolicy.backoffJitter() * ThreadLocalRandom.current().nextDouble();
        return (long) (capped - jitter);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TypeSafeAPIConnectionException("Interrupted during retry backoff", e);
        }
    }

    private SystemOneResult handleResponse(Transport.Response response) {
        int status = response.status();
        if (status >= 200 && status < 300) {
            return Json.parseResult(response.body());
        }
        throw apiException(status, response.body(), response.headers());
    }

    private static TypeSafeAPIException apiException(int status, String body, Map<String, String> headers) {
        switch (status) {
            case 400: return new TypeSafeBadRequestException(body, headers);
            case 401: return new TypeSafeAuthenticationException(body, headers);
            case 403: return new TypeSafePermissionDeniedException(body, headers);
            case 404: return new TypeSafeNotFoundException(body, headers);
            case 422: return new TypeSafeUnprocessableEntityException(body, headers);
            case 429: return new TypeSafeRateLimitException(body, headers);
            default:
                if (status >= 500) {
                    return new TypeSafeInternalServerException(status, body, headers);
                }
                return new TypeSafeAPIException(status, body, headers);
        }
    }

    /** Immutable builder. */
    public static final class Builder {
        private final String apiKey;
        private String baseUrl;
        private String defaultModel = MODEL_JEV_LATEST;
        private RetryPolicy retryPolicy = RetryPolicy.defaults();
        private Transport transport = new JdkTransport();
        private double timeoutSeconds = 30.0;

        private Builder(String apiKey) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("apiKey must not be blank");
            }
            this.apiKey = apiKey;
        }

        /** Overrides the endpoint (e.g. a gateway URL). */
        public Builder baseUrl(String v) {
            this.baseUrl = v;
            return this;
        }

        /** Model used when a request does not pin one. Defaults to jev-latest. */
        public Builder defaultModel(String v) {
            this.defaultModel = v;
            return this;
        }

        public Builder retryPolicy(RetryPolicy v) {
            this.retryPolicy = v;
            return this;
        }

        /** Supplies the HTTP transport; primarily for tests and proxies. */
        public Builder transport(Transport v) {
            this.transport = v;
            return this;
        }

        public Builder timeoutSeconds(double v) {
            this.timeoutSeconds = v;
            return this;
        }

        public TypeSafeClient build() {
            return new TypeSafeClient(this);
        }
    }
}
