package ai.typesafe;

import ai.typesafe.model.SystemOneResult;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Live smoke test against the Vercel AI Gateway TypeSafe endpoint.
 * Skips unless AI_GATEWAY_API_KEY is set, so CI stays green without a key.
 */
class LiveGatewaySmokeIT {

    private static final String GATEWAY_BASE = "https://ai-gateway.vercel.sh/typesafe";
    private static final String GATEWAY_MODEL = "typesafe-ai/jev";

    @Test
    void evaluatesRealRequestThroughGateway() {
        String key = System.getenv("AI_GATEWAY_API_KEY");
        assumeTrue(key != null && !key.isBlank(), "AI_GATEWAY_API_KEY not set; skipping live test");

        TypeSafeClient client = TypeSafeClient.builder(key)
                .baseUrl(GATEWAY_BASE)
                .defaultModel(GATEWAY_MODEL)
                .timeoutSeconds(60.0)
                .retryPolicy(ai.typesafe.http.RetryPolicy.defaults().toBuilder()
                        .totalBudgetSeconds(90.0)
                        .build())
                .build();

        SystemOneResult result = client.evaluate(
                ai.typesafe.model.EvaluationRequest
                        .of("Help! My payouts have been failing for 3 days and I need this fixed now.")
                        .noul("is_urgent", "Does this convey urgency?",
                                "Explicitly time-sensitive", "No urgency expressed")
                        .choice("department", "Which team should handle this?", Map.of(
                                "billing", "Payments, invoicing, refunds",
                                "technical", "Bugs, outages, integrations",
                                "sales", "Pricing, upgrades, new accounts"))
                        .score("frustration", "How frustrated is the customer?",
                                java.util.List.of("Calm", "Frustrated", "Very angry"))
                        .build());

        System.out.println("model        = " + result.model());
        System.out.println("is_urgent    = " + result.noul("is_urgent").noul());
        System.out.println("department   = " + result.choice("department").choice()
                + " (confidence " + result.choice("department").confidenceOrZero() + ")");
        System.out.println("frustration  = " + result.score("frustration").score()
                + " (" + result.score("frustration").nearestLevel() + ")");
        System.out.println("usage        = in:" + result.usage().inputTokens()
                + " out:" + result.usage().outputTokens());

        assertTrue(result.noul("is_urgent").noul() > 0.5, "payout failure should read as urgent");
        assertTrue(result.choice("department").probabilities().values().stream()
                .mapToDouble(Double::doubleValue).sum() > 0.99, "probabilities should sum to ~1");
        assertTrue(result.usage().inputTokens() > 0, "usage should be reported");
    }
}
