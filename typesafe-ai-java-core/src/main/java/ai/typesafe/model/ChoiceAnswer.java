package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /**
     * Options whose probability is at least the threshold, most likely first.
     * Useful for the fan-out pattern: instead of committing to the single top
     * option, explore every option that is close enough to matter.
     *
     * @param threshold the inclusive lower probability bound (e.g. 0.2)
     * @return the matching option labels, ordered by descending probability
     */
    public List<String> optionsAbove(double threshold) {
        List<Map.Entry<String, Double>> matches = new ArrayList<>();
        for (Map.Entry<String, Double> e : probabilities.entrySet()) {
            if (e.getValue() != null && e.getValue() >= threshold) {
                matches.add(e);
            }
        }
        matches.sort(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()));
        List<String> labels = new ArrayList<>(matches.size());
        for (Map.Entry<String, Double> e : matches) {
            labels.add(e.getKey());
        }
        return labels;
    }
}
