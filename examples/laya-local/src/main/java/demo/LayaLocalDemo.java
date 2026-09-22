package demo;

import ai.typesafe.TypeSafeClient;
import ai.typesafe.model.EvaluationRequest;
import ai.typesafe.model.ModelInfo;
import ai.typesafe.model.SystemOneResult;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * End-to-end demo: run the typesafe-ai-java SDK against a self-hosted
 * laya-serve instance instead of the hosted TypeSafe API.
 *
 * <p>Prerequisite: laya-serve running on localhost:8000 (fake backend is fine):
 * <pre>
 *   pip install laya-serve
 *   laya-serve
 * </pre>
 *
 * <p>Run from this directory:
 * <pre>
 *   mvn -q compile exec:java
 * </pre>
 */
public final class LayaLocalDemo {

    private static final String BASE_URL = "http://localhost:8000";

    public static void main(String[] args) throws Exception {
        System.out.println("Laya local demo - base url: " + BASE_URL);

        if (!serverIsUp()) {
            System.err.println("""
                    laya-serve is not reachable at %s.

                    Start it first (fake backend needs no weights):
                        pip install laya-serve
                        laya-serve
                    """.formatted(BASE_URL));
            System.exit(1);
        }

        // The only difference from the hosted API: which baseUrl the client points at.
        TypeSafeClient client = TypeSafeClient.laya(BASE_URL, "local-key");

        System.out.println("\nModels known to laya-serve:");
        List<ModelInfo> models = client.listModels();
        for (ModelInfo m : models) {
            System.out.println("  - " + m.id());
        }

        System.out.println("\nEvaluating a support message with three typed questions:");
        SystemOneResult result = client.evaluate(
                EvaluationRequest.of("Help! My payouts have been failing for 3 days.")
                        .noul("is_urgent", "Does this convey urgency?")
                        .choice("department", "Which team should handle this?",
                                java.util.Map.of(
                                        "billing", "Payments, invoicing, refunds",
                                        "technical", "Bugs, outages, integrations"))
                        .score("frustration", "How frustrated is the customer?",
                                List.of("Calm", "Frustrated", "Very angry"))
                        .build());

        System.out.println("model          : " + result.model());
        System.out.println("is_urgent      : " + result.noul("is_urgent").noul()
                + "  (yes above 0.7? " + result.noul("is_urgent").isYes(0.7) + ")");
        System.out.println("department     : " + result.choice("department").choice()
                + "  confidence " + result.choice("department").confidenceOrZero());
        System.out.println("frustration    : " + result.score("frustration").score()
                + "  (" + result.score("frustration").nearestLevel() + ")");
        System.out.println("usage          : input=" + result.usage().inputTokens()
                + " output=" + result.usage().outputTokens());

        System.out.println("\nSame client code, same question types, local weights."
                + " Point baseUrl at https://api.typesafe.ai and it goes hosted.");
    }

    /** laya-serve exposes a liveness probe outside the Jev surface. */
    private static boolean serverIsUp() {
        try {
            HttpClient http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create(BASE_URL + "/healthz")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    private LayaLocalDemo() {
        throw new AssertionError("not instantiable");
    }
}
