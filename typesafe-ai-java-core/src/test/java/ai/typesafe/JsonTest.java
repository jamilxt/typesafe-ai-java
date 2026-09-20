package ai.typesafe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonTest {

    @Test
    void serializesAllThreeQuestionTypes() {
        var request = ai.typesafe.model.EvaluationRequest
                .of(java.util.Map.of("document", "I was charged twice."))
                .model("jev-latest")
                .noul("urgent", "Does this convey urgency?", "Time-sensitive", "No urgency")
                .choice("dept", "Which team?", java.util.Map.of("billing", "Payments", "technical", "Bugs"))
                .score("frustration", "How angry?", java.util.List.of("Calm", "Frustrated", "Angry"))
                .build();

        String json = ai.typesafe.json.Json.writeRequest(request);

        assertTrue(json.contains("\"type\":\"noul\""), json);
        assertTrue(json.contains("\"type\":\"choice\""), json);
        assertTrue(json.contains("\"type\":\"score\""), json);
        assertTrue(json.contains("\"criteria\":{\"true\":\"Time-sensitive\",\"false\":\"No urgency\"}"), json);
        assertTrue(json.contains("\"state\":{\"document\":\"I was charged twice.\"}"), json);
    }

    @Test
    void parsesScoreAnswerWithBetweenLevelsValue() {
        String body = """
                {
                  "model": "jev-1.13.0",
                  "answers": {
                    "frustration": { "type": "score", "score": 1.05,
                      "legend": {"0": "Calm", "1": "Frustrated", "2": "Very angry"},
                      "probabilities": {"0": 0.0, "1": 0.95, "2": 0.05},
                      "confidence": 0.92 }
                  },
                  "usage": { "input_tokens": 304, "output_tokens": 18 }
                }
                """;
        var result = ai.typesafe.json.Json.parseResult(body);
        var answer = result.score("frustration");
        assertEquals(1.05, answer.score());
        assertEquals(0.92, answer.confidenceOrZero());
        assertEquals("Frustrated", answer.nearestLevel());
        assertEquals(304, result.usage().inputTokens());
    }

    @Test
    void throwsOnUnknownAnswerType() {
        String body = """
                {"model": "m", "answers": {"q": {"type": "mystery"}}, "usage": {}}
                """;
        assertThrows(ai.typesafe.exception.TypeSafeException.class,
                () -> ai.typesafe.json.Json.parseResult(body));
    }

    @Test
    void missingAnswerThrowsHelpfully() {
        var result = ai.typesafe.json.Json.parseResult(
                "{\"model\": \"m\", \"answers\": {\"a\": {\"type\": \"noul\", \"noul\": 0.5}}, \"usage\": {}}");
        assertThrows(ai.typesafe.exception.MissingAnswerException.class, () -> result.noul("nope"));
        assertThrows(ai.typesafe.exception.AnswerTypeMismatchException.class, () -> result.choice("a"));
        assertTrue(result.answer("nope").isEmpty());
    }
}
