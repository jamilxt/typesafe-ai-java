package ai.typesafe.spring;

import ai.typesafe.TypeSafeClient;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when an API key is available: either the {@code typesafe.api-key}
 * property or the {@code TYPESAFE_API_KEY} environment variable.
 */
class TypeSafeKeyPresentCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String property = context.getEnvironment().getProperty("typesafe.api-key");
        if (property != null && !property.isBlank()) {
            return true;
        }
        String env = System.getenv(TypeSafeClient.API_KEY_ENV);
        return env != null && !env.isBlank();
    }
}
