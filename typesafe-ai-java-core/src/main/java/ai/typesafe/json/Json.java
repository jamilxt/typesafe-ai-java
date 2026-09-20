package ai.typesafe.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ai.typesafe.exception.TypeSafeException;
import ai.typesafe.model.Answer;
import ai.typesafe.model.ChoiceAnswer;
import ai.typesafe.model.EvaluationRequest;
import ai.typesafe.model.NoulAnswer;
import ai.typesafe.model.ScoreAnswer;
import ai.typesafe.model.SystemOneResult;

/**
 * Jackson wiring for the wire format. Answers are polymorphic on "type";
 * unknown fields are ignored for forward compatibility.
 */
public final class Json {

    public static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private Json() {
    }

    public static String writeRequest(EvaluationRequest request) {
        try {
            return MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            throw new TypeSafeException("Failed to serialize request", e);
        }
    }

    public static SystemOneResult parseResult(String body) {
        try {
            JsonNode root = MAPPER.readTree(body);
            String model = root.path("model").asText(null);
            UsageHolder usage = new UsageHolder(
                    root.path("usage").path("input_tokens").asLong(0),
                    root.path("usage").path("output_tokens").asLong(0));
            java.util.Map<String, Answer> answers = new java.util.LinkedHashMap<>();
            JsonNode answersNode = root.path("answers");
            if (answersNode.isObject()) {
                var fields = answersNode.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    answers.put(entry.getKey(), parseAnswer(entry.getValue()));
                }
            }
            return new SystemOneResult(model, answers, usage.toUsage());
        } catch (TypeSafeException e) {
            throw e;
        } catch (Exception e) {
            throw new TypeSafeException("Failed to parse response body", e);
        }
    }

    static Answer parseAnswer(JsonNode node) {
        String type = node.path("type").asText("");
        return switch (type) {
            case "noul" -> MAPPER.convertValue(node, NoulAnswer.class);
            case "choice" -> MAPPER.convertValue(node, ChoiceAnswer.class);
            case "score" -> MAPPER.convertValue(node, ScoreAnswer.class);
            default -> throw new TypeSafeException("Unknown answer type '" + type + "' in response");
        };
    }

    /** Bridges usage out of the parse pass without a mutable result builder. */
    record UsageHolder(long in, long out) {
        SystemOneResult.Usage toUsage() {
            return new SystemOneResult.Usage(in, out);
        }
    }
}
