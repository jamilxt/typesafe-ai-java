package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One typed question to evaluate against a state.
 *
 * <p>Three primitives exist, mirroring the TypeSafe API:
 * {@link NoulQuestion} (yes/no probability), {@link ChoiceQuestion}
 * (one option from a defined set), and {@link ScoreQuestion}
 * (a position on an ordered rubric).</p>
 *
 * <p>All questions share an {@code id} (chosen by the caller; answers come
 * back under the same id) and {@code instructions}. The {@code id} is not
 * sent to the model and never used in inference.</p>
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = NoulQuestion.class, name = "noul"),
        @JsonSubTypes.Type(value = ChoiceQuestion.class, name = "choice"),
        @JsonSubTypes.Type(value = ScoreQuestion.class, name = "score")
})
public sealed interface Question permits NoulQuestion, ChoiceQuestion, ScoreQuestion {

    /** Caller-chosen identifier; the answer is returned under this key. Not serialized (it is the map key). */
    @JsonIgnore
    String id();

    /** What the model should decide. String or structured JSON value. */
    Object instructions();

    /** Convenience factory for a yes/no question. */
    static NoulQuestion noul(String id, String instructions) {
        return NoulQuestion.of(id, instructions);
    }

    /** Convenience factory for a single-option-from-set question. */
    static ChoiceQuestion choice(String id, String instructions, Map<String, String> criteria) {
        return ChoiceQuestion.of(id, instructions, criteria);
    }

    /** Convenience factory for a rubric question with 2-10 ordered levels. */
    static ScoreQuestion score(String id, String instructions, List<String> levels) {
        return ScoreQuestion.of(id, instructions, levels);
    }

    /** Helper preserving insertion order for criteria maps. */
    static <K, V> Map<K, V> ordered(Map<K, V> source) {
        return new LinkedHashMap<>(source);
    }
}
