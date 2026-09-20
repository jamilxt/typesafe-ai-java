package ai.typesafe.exception;

/** Thrown when an answer map is missing the requested question id. */
public class MissingAnswerException extends TypeSafeException {
    public MissingAnswerException(String id) {
        super("No answer found for question id '" + id + "'");
    }
}
