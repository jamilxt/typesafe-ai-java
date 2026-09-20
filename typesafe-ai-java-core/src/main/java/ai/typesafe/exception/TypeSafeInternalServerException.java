package ai.typesafe.exception;

/** The server failed to process the request (5xx). */
public class TypeSafeInternalServerException extends TypeSafeAPIException {
    public TypeSafeInternalServerException(int status, String body, java.util.Map<String, String> headers) {
        super(status, body, headers);
    }
}
