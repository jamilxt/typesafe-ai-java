package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A System One evaluation request: a state, a model, and a map of typed
 * questions keyed by caller-chosen ids.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EvaluationRequest(
        Object state,
        String model,
        Map<String, Question> questions) {

    public EvaluationRequest {
        Objects.requireNonNull(state, "state");
        java.util.Objects.requireNonNull(questions, "questions");
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("questions must not be empty");
        }
        questions = Question.ordered(questions);
    }

    public static Builder of(Object state) {
        return new Builder(state);
    }

    public static final class Builder {
        private final Object state;
        private String model;
        private final Map<String, Question> questions = new LinkedHashMap<>();

        private Builder(Object state) {
            this.state = Objects.requireNonNull(state, "state");
        }

        /** Overrides the model; defaults to the client default (jev-latest). */
        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder noul(String id, String instructions) {
            questions.put(id, Question.noul(id, instructions));
            return this;
        }

        public Builder noul(String id, String instructions, String trueMeans, String falseMeans) {
            questions.put(id, NoulQuestion.of(id, instructions, trueMeans, falseMeans));
            return this;
        }

        public Builder choice(String id, String instructions, Map<String, String> criteria) {
            questions.put(id, Question.choice(id, instructions, criteria));
            return this;
        }

        public Builder score(String id, String instructions, java.util.List<String> levels) {
            questions.put(id, Question.score(id, instructions, levels));
            return this;
        }

        /** Adds a pre-built question under its own id. */
        public Builder question(Question q) {
            questions.put(q.id(), q);
            return this;
        }

        public EvaluationRequest build() {
            return new EvaluationRequest(state, model, questions);
        }
    }
}
