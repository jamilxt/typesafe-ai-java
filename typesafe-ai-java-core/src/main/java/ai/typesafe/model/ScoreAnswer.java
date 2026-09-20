package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

/**
 * The Score answer: a probability-weighted position along the rubric that can
 * land between levels, each level's probability (keys are string level
 * indexes), the legend mapping level number back to description, and a
 * confidence.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ScoreAnswer(
        @JsonProperty("type") String type,
        @JsonProperty("score") double score,
        @JsonProperty("legend") Map<String, String> legend,
        @JsonProperty("probabilities") Map<String, Double> probabilities,
        @JsonProperty("confidence") Double confidence) implements Answer {

    public ScoreAnswer {
        if (type == null) type = "score";
    }

    public double confidenceOrZero() {
        return confidence == null ? 0.0 : confidence;
    }

    public Optional<Double> confidenceOptional() {
        return Optional.ofNullable(confidence);
    }

    /** The level description nearest to the score (rounds to closest index). */
    public String nearestLevel() {
        if (legend == null || legend.isEmpty()) {
            return null;
        }
        int idx = Math.max(0, Math.min(legend.size() - 1, (int) Math.round(score)));
        return legend.get(String.valueOf(idx));
    }
}
