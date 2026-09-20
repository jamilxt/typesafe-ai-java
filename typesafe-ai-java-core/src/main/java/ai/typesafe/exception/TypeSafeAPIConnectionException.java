package ai.typesafe.exception;

/** A request failed without an HTTP response. */
public class TypeSafeAPIConnectionException extends TypeSafeException {
    public TypeSafeAPIConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
