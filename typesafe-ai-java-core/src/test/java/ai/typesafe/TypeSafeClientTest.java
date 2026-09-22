package ai.typesafe;

import ai.typesafe.http.RetryPolicy;
import ai.typesafe.http.Transport;
import ai.typesafe.model.SystemOneResult;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TypeSafeClientTest {

    private static final String OK_BODY = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "is_urgent": { "type": "noul", "noul": 0.95 },
                "department": { "type": "choice", "choice": "billing",
                  "probabilities": {"billing": 0.88, "technical": 0.12, "sales": 0.0},
                  "confidence": 0.81 },
                "frustration": { "type": "score", "score": 1.05,
                  "legend": {"0": "Calm", "1": "Frustrated", "2": "Very angry"},
                  "probabilities": {"0": 0.0, "1": 0.95, "2": 0.05},
                  "confidence": 0.92 }
              },
              "usage": { "input_tokens": 318, "output_tokens": 34 }
            }
            """;

    @Test
    void evaluatesAndReturnsTypedAnswers() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            assertTrue(body.contains("\"state\":\"Help!"), "request body should contain state: " + body);
            assertTrue(body.contains("\"model\":\"jev-latest\""), "default model injected: " + body);
            assertTrue(body.contains("\"is_urgent\""), body);
            assertTrue(key.equals("test-key"), "bearer key passed");
            return new Transport.Response(200, Map.of("content-type", "application/json"), OK_BODY);
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.none())
                .build();

        SystemOneResult result = client.evaluate(ai.typesafe.model.EvaluationRequest
                .of("Help! My payouts have been failing for 3 days.")
                .noul("is_urgent", "Does this convey urgency?")
                .choice("department", "Which team should handle this?",
                        Map.of("billing", "Payments and refunds", "technical", "Bugs and outages", "sales", "Pricing"))
                .score("frustration", "How frustrated is the customer?",
                        java.util.List.of("Calm", "Frustrated", "Very angry"))
                .build());

        assertEquals(1, calls.get());
        assertEquals("jev-1.13.0", result.model());
        assertEquals(0.95, result.noul("is_urgent").noul());
        assertTrue(result.noul("is_urgent").isYes(0.7));
        assertEquals("billing", result.choice("department").choice());
        assertEquals(0.88, result.choice("department").probabilityOf("billing"));
        assertEquals(0.81, result.choice("department").confidenceOrZero());
        assertEquals(1.05, result.score("frustration").score());
        assertEquals("Frustrated", result.score("frustration").nearestLevel());
        assertEquals(318, result.usage().inputTokens());
        assertEquals(34, result.usage().outputTokens());
    }

    @Test
    void retriesOn429ThenSucceeds_andHonorsRetryAfter() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            if (calls.incrementAndGet() == 1) {
                return new Transport.Response(429, Map.of("retry-after-ms", "10"), "{\"error\":\"rate limited\"}");
            }
            return new Transport.Response(200, Map.of(), OK_BODY);
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .backoffInitial(java.time.Duration.ofMillis(1))
                        .backoffMax(java.time.Duration.ofMillis(5))
                        .totalBudgetSeconds(5.0)
                        .build())
                .build();

        SystemOneResult result = client.evaluate(smallRequest());
        assertEquals(2, calls.get());
        assertEquals("jev-1.13.0", result.model());
    }

    @Test
    void doesNotRetryOn401() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            return new Transport.Response(401, Map.of(), "{\"error\":\"bad key\"}");
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .backoffInitial(java.time.Duration.ofMillis(1)).build())
                .build();

        assertThrows(ai.typesafe.exception.TypeSafeAuthenticationException.class,
                () -> client.evaluate(smallRequest()));
        assertEquals(1, calls.get());
    }

    @Test
    void exhaustsRetriesOn529() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            return new Transport.Response(529, Map.of(), "{\"error\":\"overloaded\"}");
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .maxRetries(2)
                        .backoffInitial(java.time.Duration.ofMillis(1))
                        .backoffMax(java.time.Duration.ofMillis(2))
                        .totalBudgetSeconds(5.0)
                        .build())
                .build();

        assertThrows(ai.typesafe.exception.TypeSafeInternalServerException.class,
                () -> client.evaluate(smallRequest()));
        assertEquals(3, calls.get()); // initial + 2 retries
    }

    @Test
    void listModelsRetriesOn429ThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            assertTrue(url.endsWith("/v1/models"), "listModels should hit /v1/models: " + url);
            if (calls.get() == 1) {
                return new Transport.Response(429, Map.of("retry-after-ms", "1"), "{\"error\":\"rate limited\"}");
            }
            return new Transport.Response(200, Map.of(),
                    "{\"models\":[\"jev-latest\",{\"id\":\"jev-1.13.0\",\"created\":1758000000}]}");
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .backoffInitial(java.time.Duration.ofMillis(1)).build())
                .build();

        java.util.List<ai.typesafe.model.ModelInfo> models = client.listModels();
        assertEquals(2, calls.get());
        assertEquals(2, models.size());
        assertEquals("jev-latest", models.get(0).id());
        assertEquals(Map.of(), models.get(0).metadata());
        assertEquals("jev-1.13.0", models.get(1).id());
        assertEquals(1758000000, models.get(1).number("created").longValue());
    }

    @Test
    void listModelsRawReturnsBody() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            return new Transport.Response(200, Map.of(), "{\"models\":[\"jev-latest\"]}");
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.none())
                .build();

        String body = client.listModelsRaw();
        assertEquals(1, calls.get());
        assertTrue(body.contains("jev-latest"));
    }

    @Test
    void rotatedApiKeySupplierIsUsedPerRequest() {
        AtomicInteger calls = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<String> currentKey =
                new java.util.concurrent.atomic.AtomicReference<>("key-v1");
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            if (calls.get() == 1) {
                assertEquals("key-v1", key);
                return new Transport.Response(401, Map.of(), "{\"error\":\"bad key\"}");
            }
            assertEquals("key-v2", key, "second call must pick up the rotated key");
            return new Transport.Response(200, Map.of(), OK_BODY);
        };

        // retryPolicy.none() so the 401 surfaces; the point is the key the transport saw
        TypeSafeClient client = TypeSafeClient.builder(currentKey::get)
                .transport(transport)
                .retryPolicy(RetryPolicy.none())
                .build();

        assertThrows(ai.typesafe.exception.TypeSafeAuthenticationException.class,
                () -> client.evaluate(smallRequest()));
        currentKey.set("key-v2");
        SystemOneResult result = client.evaluate(smallRequest());
        assertEquals("jev-1.13.0", result.model());
        assertEquals(2, calls.get());
    }

    @Test
    void layaFactoryPointsClientAtGivenBaseUrl() {
        TypeSafeClient client = TypeSafeClient.laya("http://localhost:8000", "local-key");
        assertEquals("http://localhost:8000", client.baseUrl());
        assertEquals(TypeSafeClient.MODEL_JEV_LATEST, client.defaultModel());
    }

    @Test
    void baseUrlOverrideRoutesRequestsToLocalEndpoint() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            assertTrue(url.startsWith("http://localhost:8000"), "local base url used: " + url);
            assertEquals("local-key", key);
            // laya-serve answers in the same wire shape as the hosted API
            return new Transport.Response(200, Map.of(), OK_BODY);
        };

        TypeSafeClient client = TypeSafeClient.builder("local-key")
                .baseUrl("http://localhost:8000/")
                .transport(transport)
                .retryPolicy(RetryPolicy.none())
                .build();

        SystemOneResult result = client.evaluate(smallRequest());
        assertEquals(1, calls.get());
        assertEquals("jev-1.13.0", result.model());
    }

    @Test
    void listModelsUsesGetMethodWhenTransportSupportsIt() {
        AtomicInteger getCalls = new AtomicInteger();
        AtomicInteger postCalls = new AtomicInteger();
        Transport transport = new Transport() {
            @Override
            public Response postJson(String url, String apiKey, String jsonBody, double timeoutSeconds) {
                postCalls.incrementAndGet();
                return new Response(405, Map.of(), "{\"error\":\"method not allowed\"}");
            }

            @Override
            public Response getJson(String url, String apiKey, double timeoutSeconds) {
                getCalls.incrementAndGet();
                assertTrue(url.endsWith("/v1/models"), url);
                return new Response(200, Map.of(),
                        "{\"models\":[{\"name\":\"laya-english\",\"description\":\"local\"}]}");
            }
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.none())
                .build();

        java.util.List<ai.typesafe.model.ModelInfo> models = client.listModels();
        assertEquals(1, getCalls.get());
        assertEquals(0, postCalls.get());
        assertEquals("laya-english", models.get(0).id());
        assertEquals("local", models.get(0).string("description"));
    }

    @Test
    void optionsAboveOrdersByDescendingProbability() {
        ai.typesafe.model.ChoiceAnswer answer = new ai.typesafe.model.ChoiceAnswer(
                "choice", "billing",
                new java.util.LinkedHashMap<>(Map.of(
                        "billing", 0.55, "technical", 0.40, "sales", 0.05)),
                0.6);
        assertEquals(java.util.List.of("billing", "technical"), answer.optionsAbove(0.2));
        assertEquals(java.util.List.of("billing"), answer.optionsAbove(0.5));
        assertTrue(answer.optionsAbove(0.9).isEmpty());
    }

    @Test
    void listModelsDoesNotRetryOn401() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            return new Transport.Response(401, Map.of(), "{\"error\":\"bad key\"}");
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .backoffInitial(java.time.Duration.ofMillis(1)).build())
                .build();

        assertThrows(ai.typesafe.exception.TypeSafeAuthenticationException.class, client::listModels);
        assertEquals(1, calls.get());
    }

    @Test
    void malformedRetryAfterMsDoesNotCrashRetryLoop() {
        AtomicInteger calls = new AtomicInteger();
        Transport transport = (url, key, body, timeout) -> {
            calls.incrementAndGet();
            if (calls.get() == 1) {
                // Malformed ms header must be ignored (fall back to seconds header / backoff), not throw NFE
                return new Transport.Response(429, Map.of("retry-after-ms", "soon"), "{\"error\":\"rate limited\"}");
            }
            return new Transport.Response(200, Map.of(), OK_BODY);
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .backoffInitial(java.time.Duration.ofMillis(1)).build())
                .build();

        SystemOneResult result = client.evaluate(smallRequest());
        assertEquals(2, calls.get());
        assertNotNull(result);
    }

    @Test
    void requestBuilderRejectsEmptyQuestions() {
        assertThrows(IllegalArgumentException.class,
                () -> ai.typesafe.model.EvaluationRequest.of("state").build());
    }

    @Test
    void choiceRejectsTooManyOptions() {
        var criteria = new java.util.LinkedHashMap<String, String>();
        for (int i = 0; i < 256; i++) {
            criteria.put("opt" + i, "d");
        }
        assertThrows(IllegalArgumentException.class,
                () -> ai.typesafe.model.ChoiceQuestion.of("c", "pick", criteria));
    }

    @Test
    void scoreRejectsWrongLevelCount() {
        assertThrows(IllegalArgumentException.class,
                () -> ai.typesafe.model.ScoreQuestion.of("s", "rate", java.util.List.of("only-one")));
    }

    private ai.typesafe.model.EvaluationRequest smallRequest() {
        return ai.typesafe.model.EvaluationRequest.of("state text")
                .noul("q1", "Is this true?")
                .build();
    }
}
