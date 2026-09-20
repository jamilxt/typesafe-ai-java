package ai.typesafe.exception;

/** Access was denied (403). */
public class TypeSafePermissionDeniedException extends TypeSafeAPIException {
    public TypeSafePermissionDeniedException(String body, java.util.Map<String, String> headers) {
        super(403, body, headers);
    }
}
