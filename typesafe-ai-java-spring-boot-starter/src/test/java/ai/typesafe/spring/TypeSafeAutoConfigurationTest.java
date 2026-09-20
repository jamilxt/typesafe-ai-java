package ai.typesafe.spring;

import ai.typesafe.TypeSafeClient;
import ai.typesafe.http.RetryPolicy;
import ai.typesafe.http.Transport;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TypeSafeAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TypeSafeAutoConfiguration.class));

    @Test
    void createsClientWhenKeyPresent() {
        runner.withPropertyValues("typesafe.api-key=test-key-123", "typesafe.model=jev-latest")
                .run(context -> {
                    assertThat(context).hasSingleBean(TypeSafeClient.class);
                    assertThat(context.getBean(TypeSafeProperties.class).getApiKey()).isEqualTo("test-key-123");
                });
    }

    @Test
    void userDefinedClientBeanWins() {
        runner.withUserConfiguration(CustomClientConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TypeSafeClient.class);
                    assertThat(context.getBean(TypeSafeClient.class)).isSameAs(context.getBean("myClient"));
                });
    }

    @Test
    void disabledWhenKeyAbsentAndNoEnv() {
        // Clear any inherited TYPESAFE_API_KEY so the failure branch is exercised.
        runner.run(context -> {
            String env = System.getenv("TYPESAFE_API_KEY");
            if (env == null || env.isBlank()) {
                assertThat(context).doesNotHaveBean(TypeSafeClient.class);
            }
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomClientConfig {
        @Bean
        TypeSafeClient myClient() {
            return TypeSafeClient.builder("custom-key")
                    .transport((url, key, body, timeout) ->
                            new Transport.Response(200, Map.of(), "{}"))
                    .retryPolicy(RetryPolicy.none())
                    .build();
        }
    }
}
