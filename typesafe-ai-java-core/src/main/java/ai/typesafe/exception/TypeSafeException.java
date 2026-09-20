package ai.typesafe.exception;

/** Base exception for all SDK failures, mirroring the official SDKs. */
public class TypeSafeException extends RuntimeException {
    public TypeSafeException(String message) {
        super(message);
    }

    public TypeSafeException(String message, Throwable cause) {
        super(message, cause);
    }
}
