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
            return new Transport.Response(200, Map.of(), "{\"models\":[\"jev-latest\",\"jev-1.13.0\"]}");
        };

        TypeSafeClient client = TypeSafeClient.builder("test-key")
                .transport(transport)
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .backoffInitial(java.time.Duration.ofMillis(1)).build())
                .build();

        String body = client.listModels();
        assertEquals(2, calls.get());
        assertTrue(body.contains("jev-1.13.0"));
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
