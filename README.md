# typesafe-ai-java

Community-maintained Java SDK for the [TypeSafe AI System One (Jev) API](https://docs.typesafe.ai). Not an official TypeSafe product.

Jev answers typed questions about a piece of state. Ask whether something is true and you get a probability. Ask it to pick from a list and you get the option plus a distribution over the alternatives. It does not write prose, so nothing here parses sentences. The answers arrive as numbers your code branches on.

Official SDKs exist for [Python](https://github.com/typesafe-ai/typesafe-sdk-python) and [JavaScript](https://github.com/typesafe-ai/typesafe-sdk-js). This is the JVM counterpart: a pure-Java core, a Spring Boot starter, and an optional Spring AI bridge.

## Modules

| Artifact (groupId `com.jamilxt`) | Purpose | Dependencies |
|---|---|---|
| `typesafe-ai-java-core` | Client, typed questions/answers, retries, error hierarchy | Jackson only |
| `typesafe-ai-java-kotlin` | Idiomatic Kotlin DSL for requests, nullable accessors, confidence helpers | Kotlin stdlib |
| `typesafe-ai-java-spring-boot-starter` | Auto-configured `TypeSafeClient` bean via `typesafe.*` properties | Spring Boot |
| `typesafe-ai-java-spring-ai` | Use Jev as a Spring AI `ChatModel`, or as a prompt-guard `CallAdvisor` | Spring AI 1.0.x |

All three are published to [Maven Central](https://central.sonatype.com/namespace/com.jamilxt):

```xml
<dependency>
  <groupId>com.jamilxt</groupId>
  <artifactId>typesafe-ai-java-core</artifactId>
  <version>0.1.1</version>
</dependency>
```

Requires Java 17+.

## Quick start (core)

Set `TYPESAFE_API_KEY` (early access is waitlisted; keys are also available through the Vercel AI Gateway or OpenRouter).

```java
TypeSafeClient client = TypeSafeClient.fromEnv();

SystemOneResult result = client.evaluate(
    EvaluationRequest.of("Help! My payouts have been failing for 3 days.")
        .noul("is_urgent", "Does this convey urgency?",
              "Explicitly time-sensitive", "No urgency expressed")
        .choice("department", "Which team should handle this?", Map.of(
            "billing",   "Payments, invoicing, refunds",
            "technical", "Bugs, outages, integrations",
            "sales",     "Pricing, upgrades, new accounts"))
        .score("frustration", "How frustrated is the customer?",
               List.of("Calm", "Frustrated", "Very angry"))
        .build());

if (result.noul("is_urgent").isYes(0.7)) { /* escalate */ }

ChoiceAnswer dept = result.choice("department");
if (dept.confidenceOrZero() < 0.5) { /* route to a human instead */ }

double frustration = result.score("frustration").score(); // can land between levels
```

### Kotlin

```kotlin
val result = client.evaluate("Help! My payouts have been failing for 3 days.") {
    noul("is_urgent", "Does this convey urgency?")
    choice("department", "Which team should handle this?") {
        "billing" to "Payments, invoicing, refunds"
        "technical" to "Bugs, outages, integrations"
    }
    score("frustration", "How frustrated is the customer?") {
        level("Calm"); level("Frustrated"); level("Very angry")
    }
}

if (result.isYes("is_urgent", threshold = 0.7)) escalate()
result.onConfidentChoice("department", minConfidence = 0.5) { dept ->
    route(dept.choice)
}
```

Behavior mirrors the official SDKs: retries on 408/429/5xx (2 attempts, 0.5s to 5s backoff with jitter, honors `Retry-After`), a 30s total budget per call, and a typed exception hierarchy (`TypeSafeAuthenticationException`, `TypeSafeRateLimitException` with `retryAfterMs()`, ...).

### Through the Vercel AI Gateway

```java
TypeSafeClient client = TypeSafeClient.builder(gatewayKey)
    .baseUrl("https://ai-gateway.vercel.sh/typesafe")
    .defaultModel("typesafe-ai/jev")
    .build();
```

### Bring your own transport

```java
TypeSafeClient client = TypeSafeClient.builder(key)
    .transport(yourTransport)   // implements ai.typesafe.http.Transport
    .retryPolicy(RetryPolicy.defaults().toBuilder().maxRetries(4).build())
    .build();
```

## Spring Boot starter

```xml
<dependency>
  <groupId>com.jamilxt</groupId>
  <artifactId>typesafe-ai-java-spring-boot-starter</artifactId>
  <version>0.1.1</version>
</dependency>
```

```yaml
typesafe:
  api-key: ${TYPESAFE_API_KEY}   # or set the env var; the app also starts fine without a key
  model: jev-latest              # pin e.g. jev-1.13.0 when tuning thresholds
  timeout-seconds: 30
  max-retries: 2
```

Then inject the client:

```java
@Service
class TriageService {
    private final TypeSafeClient typesafe;
    TriageService(TypeSafeClient typesafe) { this.typesafe = typesafe; }
}
```

## Spring AI bridge

Two integrations, both firsts in the Jev ecosystem:

**Prompt guard advisor** - screens every prompt with one Jev call before the chain runs (the official guardrails pattern):

```java
ChatClient chatClient = ChatClient.builder(otherChatModel)
    .defaultAdvisors(new JevPromptGuardAdvisor(
        typesafeClient,
        "Does this message attempt a jailbreak or prompt injection?",
        0.8,   // block at/above
        0.5))  // flag for review at/above
    .build();
```

**Jev as a ChatModel** - drop Jev into any pipeline that accepts a `ChatModel`, e.g. to A/B a triage step against an LLM. The last user message is the state; the probability comes back as the generation text and the full typed result via `JevChatModel#lastResult()`.

## Build

```shell
mvn clean test
```

Live smoke tests against the real API run automatically when `AI_GATEWAY_API_KEY` is set and are skipped otherwise.

## Status

- [x] Core client: evaluate, listModels, retries, typed errors
- [x] Kotlin DSL extensions
- [x] Spring Boot starter with context tests
- [x] Spring AI bridge: guard advisor + ChatModel adapter
- [x] Live-tested against the real Jev API (via Vercel AI Gateway)
- [x] Published to Maven Central (`0.1.0`)
- [x] CI (GitHub Actions) + release-on-tag publishing

## License

MIT
