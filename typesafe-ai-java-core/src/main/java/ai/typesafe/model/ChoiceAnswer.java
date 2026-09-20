package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * The Choice answer: the highest-probability option, the distribution across
 * every option, and a confidence derived from that distribution.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChoiceAnswer(
        @JsonProperty("type") String type,
        @JsonProperty("choice") String choice,
        @JsonProperty("probabilities") Map<String, Double> probabilities,
        @JsonProperty("confidence") Double confidence) implements Answer {

    public ChoiceAnswer {
        if (type == null) type = "choice";
    }

    /**
     * Confidence between 0 and 1, or 0.0 when the API omits it.
     * Use {@link #confidenceOptional()} to distinguish an omitted value.
     */
    public double confidenceOrZero() {
        return confidence == null ? 0.0 : confidence;
    }

    public java.util.Optional<Double> confidenceOptional() {
        return java.util.Optional.ofNullable(confidence);
    }

    /** Probability of a given option, or 0.0 when absent from the distribution. */
    public double probabilityOf(String option) {
        Double p = probabilities.get(option);
        return p == null ? 0.0 : p;
    }
}
