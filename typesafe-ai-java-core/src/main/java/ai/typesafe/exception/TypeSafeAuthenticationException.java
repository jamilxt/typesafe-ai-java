package ai.typesafe.exception;

/** Authentication failed (401). */
public class TypeSafeAuthenticationException extends TypeSafeAPIException {
    public TypeSafeAuthenticationException(String body, java.util.Map<String, String> headers) {
        super(401, body, headers);
    }
}
