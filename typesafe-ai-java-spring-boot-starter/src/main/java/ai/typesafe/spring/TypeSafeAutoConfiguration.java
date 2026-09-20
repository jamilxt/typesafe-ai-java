package ai.typesafe.spring;

import ai.typesafe.TypeSafeClient;
import ai.typesafe.http.RetryPolicy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Auto-configures a shared {@link TypeSafeClient} bean.
 *
 * <p>Activated only when an API key is available (the {@code typesafe.api-key}
 * property or the {@code TYPESAFE_API_KEY} environment variable). Defining
 * your own {@code TypeSafeClient} bean disables this configuration, so the
 * application starts fine without any key configured.</p>
 */
@Configuration
@Conditional(TypeSafeKeyPresentCondition.class)
@ConditionalOnMissingBean(TypeSafeClient.class)
@EnableConfigurationProperties(TypeSafeProperties.class)
public class TypeSafeAutoConfiguration {

    @Bean
    public TypeSafeClient typeSafeClient(TypeSafeProperties props) {
        String key = (props.getApiKey() == null || props.getApiKey().isBlank())
                ? System.getenv(TypeSafeClient.API_KEY_ENV)
                : props.getApiKey();
        TypeSafeClient.Builder builder = TypeSafeClient.builder(key)
                .defaultModel(props.getModel())
                .timeoutSeconds(props.getTimeoutSeconds())
                .retryPolicy(RetryPolicy.defaults().toBuilder()
                        .maxRetries(props.getMaxRetries())
                        .backoffInitial(Duration.ofMillis(500))
                        .build());
        if (props.getBaseUrl() != null && !props.getBaseUrl().isBlank()) {
            builder.baseUrl(props.getBaseUrl());
        }
        return builder.build();
    }
}
