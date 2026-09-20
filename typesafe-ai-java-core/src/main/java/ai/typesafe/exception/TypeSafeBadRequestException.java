package ai.typesafe.exception;

/** The request was invalid (400). */
public class TypeSafeBadRequestException extends TypeSafeAPIException {
    public TypeSafeBadRequestException(String body, java.util.Map<String, String> headers) {
        super(400, body, headers);
    }
}
