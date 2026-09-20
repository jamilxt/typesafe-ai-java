package ai.typesafe.exception;

/** The request failed server-side validation (422). */
public class TypeSafeUnprocessableEntityException extends TypeSafeAPIException {
    public TypeSafeUnprocessableEntityException(String body, java.util.Map<String, String> headers) {
        super(422, body, headers);
    }
}
