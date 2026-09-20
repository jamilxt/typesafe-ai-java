package ai.typesafe.exception;

/** The resource was not found (404). */
public class TypeSafeNotFoundException extends TypeSafeAPIException {
    public TypeSafeNotFoundException(String body, java.util.Map<String, String> headers) {
        super(404, body, headers);
    }
}
