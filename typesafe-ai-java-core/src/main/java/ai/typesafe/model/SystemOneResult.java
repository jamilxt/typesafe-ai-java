package ai.typesafe.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;
import java.util.Optional;

/**
 * The evaluation response: the model that answered, one answer per question
 * id, and token usage.
 *
 * <p>Typed accessors {@link #noul(String)}, {@link #choice(String)} and
 * {@link #score(String)} throw {@link ai.typesafe.exception.AnswerTypeMismatchException}
 * if the answer under that id is not of the requested kind, and return an
 * empty Optional for an unknown id only via {@link #answer(String)}.</p>
 */
public record SystemOneResult(
        String model,
        Map<String, Answer> answers,
        Usage usage) {

    /** Typed lookup. Empty when the id is absent. */
    public Optional<Answer> answer(String id) {
        return Optional.ofNullable(answers.get(id));
    }

    public NoulAnswer noul(String id) {
        return requireType(id, NoulAnswer.class);
    }

    public ChoiceAnswer choice(String id) {
        return requireType(id, ChoiceAnswer.class);
    }

    public ScoreAnswer score(String id) {
        return requireType(id, ScoreAnswer.class);
    }

    private <T extends Answer> T requireType(String id, Class<T> type) {
        Answer a = answers.get(id);
        if (a == null) {
            throw new ai.typesafe.exception.MissingAnswerException(id);
        }
        if (!type.isInstance(a)) {
            throw new ai.typesafe.exception.AnswerTypeMismatchException(id, a.getClass().getSimpleName(), type.getSimpleName());
        }
        return type.cast(a);
    }

    /** Token usage for the request. */
    public record Usage(long inputTokens, long outputTokens) {
    }
}
