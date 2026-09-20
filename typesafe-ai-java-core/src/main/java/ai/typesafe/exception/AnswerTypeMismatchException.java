package ai.typesafe.exception;

/** Thrown when an answer exists but is not of the requested type. */
public class AnswerTypeMismatchException extends TypeSafeException {
    public AnswerTypeMismatchException(String id, String actualType, String requestedType) {
        super("Answer for question id '" + id + "' is of type '" + actualType
                + "' but was requested as '" + requestedType + "'");
    }
}
