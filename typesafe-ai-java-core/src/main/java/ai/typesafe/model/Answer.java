package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A typed answer to one question, returned under the question's id.
 * Three shapes exist, matching the three question primitives.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = NoulAnswer.class, name = "noul"),
        @JsonSubTypes.Type(value = ChoiceAnswer.class, name = "choice"),
        @JsonSubTypes.Type(value = ScoreAnswer.class, name = "score")
})
public sealed interface Answer permits NoulAnswer, ChoiceAnswer, ScoreAnswer {
    /** The discriminator: "noul", "choice", or "score". */
    String type();
}
