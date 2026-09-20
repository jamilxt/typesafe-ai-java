package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Objects;

/**
 * A yes/no question. The answer is the probability that the answer is yes.
 *
 * @param id           caller-chosen identifier
 * @param instructions the yes/no question (string or structured JSON value)
 * @param criteria     optional descriptions of what yes and no mean
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoulQuestion(
        String id,
        Object instructions,
        @JsonProperty("criteria") NoulCriteria criteria) implements Question {

    public NoulQuestion {
        Objects.requireNonNull(instructions, "instructions");
    }

    public static NoulQuestion of(String id, String instructions) {
        return new NoulQuestion(id, instructions, null);
    }

    public static NoulQuestion of(String id, String instructions, String trueMeans, String falseMeans) {
        return new NoulQuestion(id, instructions, new NoulCriteria(trueMeans, falseMeans));
    }

    /** Optional descriptions of what a yes and a no mean. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record NoulCriteria(
            @JsonProperty("true") String trueMeans,
            @JsonProperty("false") String falseMeans) {
    }
}
