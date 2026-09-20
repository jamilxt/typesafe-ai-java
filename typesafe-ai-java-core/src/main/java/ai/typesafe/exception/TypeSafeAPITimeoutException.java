package ai.typesafe.exception;

/** A request exceeded its configured timeout. */
public class TypeSafeAPITimeoutException extends TypeSafeAPIConnectionException {
    public TypeSafeAPITimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
