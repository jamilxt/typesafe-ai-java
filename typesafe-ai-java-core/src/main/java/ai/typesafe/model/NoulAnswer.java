package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** The yes/no answer: probability that the answer is yes, from 0 to 1. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NoulAnswer(
        @JsonProperty("type") String type,
        @JsonProperty("noul") double noul) implements Answer {

    public NoulAnswer {
        if (type == null) type = "noul";
    }

    /** True when the probability is at or above the given threshold. */
    public boolean isYes(double threshold) {
        return noul >= threshold;
    }
}
