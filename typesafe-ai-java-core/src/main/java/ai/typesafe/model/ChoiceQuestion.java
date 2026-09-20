package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Picks one option from a set you define. The answer includes the chosen
 * option, the full probability distribution, and a confidence.
 *
 * <p>Maximum 255 options per Choice (API limit).</p>
 *
 * @param id           caller-chosen identifier
 * @param instructions what the model should decide (string or structured JSON value)
 * @param criteria     map of option key to rubric description; use {@code ""} or a
 *                     space when an option needs no extra detail
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChoiceQuestion(
        String id,
        Object instructions,
        Map<String, String> criteria) implements Question {

    /** The TypeSafe API accepts at most 255 options per Choice question. */
    public static final int MAX_OPTIONS = 255;

    public ChoiceQuestion {
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.isEmpty()) {
            throw new IllegalArgumentException("Choice question '" + id + "' needs at least one option");
        }
        if (criteria.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException(
                    "Choice question '" + id + "' has " + criteria.size() + " options; the API allows at most " + MAX_OPTIONS);
        }
        criteria = Question.ordered(criteria);
    }

    public static ChoiceQuestion of(String id, String instructions, Map<String, String> criteria) {
        return new ChoiceQuestion(id, instructions, criteria);
    }

    /** Fluent option adder preserving order. */
    public static Builder builder(String id, String instructions) {
        return new Builder(id, instructions);
    }

    public static final class Builder {
        private final String id;
        private final Object instructions;
        private final Map<String, String> criteria = new LinkedHashMap<>();

        private Builder(String id, String instructions) {
            this.id = Objects.requireNonNull(id, "id");
            this.instructions = Objects.requireNonNull(instructions, "instructions");
        }

        /** Adds an option with a rubric description. */
        public Builder option(String key, String description) {
            criteria.put(key, description);
            return this;
        }

        /** Adds an option without extra description (serialized as empty string). */
        public Builder option(String key) {
            criteria.put(key, "");
            return this;
        }

        public ChoiceQuestion build() {
            return new ChoiceQuestion(id, instructions, criteria);
        }
    }
}
