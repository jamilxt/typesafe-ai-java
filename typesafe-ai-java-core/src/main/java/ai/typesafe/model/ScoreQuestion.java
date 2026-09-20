package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Rates the state along an ordered rubric you define. The answer includes a
 * probability-weighted score that can land between levels, each level's
 * probability, and a confidence.
 *
 * <p>The API accepts between 2 and 10 levels.</p>
 *
 * @param id           caller-chosen identifier
 * @param instructions what the model should rate (string or structured JSON value)
 * @param criteria     ordered level descriptions (2-10 entries)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ScoreQuestion(
        String id,
        Object instructions,
        List<String> criteria) implements Question {

    public static final int MIN_LEVELS = 2;
    public static final int MAX_LEVELS = 10;

    public ScoreQuestion {
        Objects.requireNonNull(instructions, "instructions");
        Objects.requireNonNull(criteria, "criteria");
        if (criteria.size() < MIN_LEVELS || criteria.size() > MAX_LEVELS) {
            throw new IllegalArgumentException(
                    "Score question '" + id + "' needs between " + MIN_LEVELS + " and " + MAX_LEVELS
                            + " levels; got " + criteria.size());
        }
        criteria = List.copyOf(criteria);
    }

    public static ScoreQuestion of(String id, String instructions, List<String> levels) {
        return new ScoreQuestion(id, instructions, levels);
    }
}
