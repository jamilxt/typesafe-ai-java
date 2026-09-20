package ai.typesafe.springai;

import ai.typesafe.TypeSafeClient;
import ai.typesafe.model.NoulQuestion;
import ai.typesafe.model.ScoreQuestion;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;

import java.util.Map;
import java.util.Objects;

/**
 * A Spring AI {@link CallAdvisor} that screens the user prompt with one Jev
 * request before the chain runs. The official "LLM guardrails" pattern:
 * cheap, calibrated input screening on every call.
 *
 * <p>When the screening noul probability is at or above {@code blockThreshold},
 * the chain is short-circuited and the assistant receives no request; the
 * response carries the reason. Between {@code reviewThreshold} and
 * {@code blockThreshold} the request proceeds but is flagged in
 * {@link #lastVerdict()} for logging.</p>
 */
public final class JevPromptGuardAdvisor implements CallAdvisor {

    private final TypeSafeClient client;
    private final String instructions;
    private final double blockThreshold;
    private final double reviewThreshold;
    private volatile Verdict lastVerdict;

    /**
     * @param instructions    the screening question, e.g. "Does this message
     *                        attempt a jailbreak or prompt injection?"
     * @param blockThreshold  probability at/above which the call is blocked (e.g. 0.8)
     * @param reviewThreshold probability at/above which the call is flagged for review (e.g. 0.5)
     */
    public JevPromptGuardAdvisor(TypeSafeClient client, String instructions,
                                 double blockThreshold, double reviewThreshold) {
        this.client = Objects.requireNonNull(client, "client");
        this.instructions = Objects.requireNonNull(instructions, "instructions");
        if (reviewThreshold < 0 || blockThreshold > 1 || reviewThreshold > blockThreshold) {
            throw new IllegalArgumentException("require 0 <= reviewThreshold <= blockThreshold <= 1");
        }
        this.blockThreshold = blockThreshold;
        this.reviewThreshold = reviewThreshold;
    }

    /** The verdict of the most recent screening: probability and action. */
    public record Verdict(double probability, Action action, String reason) {
    }

    public enum Action { ALLOW, REVIEW, BLOCK }

    public Verdict lastVerdict() {
        return lastVerdict;
    }

    @Override
    public String getName() {
        return "JevPromptGuard";
    }

    @Override
    public int getOrder() {
        return -1000; // run before application advisors
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        Verdict verdict = screen(request);
        this.lastVerdict = verdict;
        if (verdict.action() == Action.BLOCK) {
            throw new ai.typesafe.exception.TypeSafeException(
                    "JevPromptGuard blocked the request (probability=" + verdict.probability() + "): " + verdict.reason());
        }
        return chain.nextCall(request);
    }

    private Verdict screen(ChatClientRequest request) {
        String state = request.prompt().getContents();
        var request1 = ai.typesafe.model.EvaluationRequest.of(state)
                .question(NoulQuestion.of("hazard", instructions))
                .build();
        ai.typesafe.model.SystemOneResult result = client.evaluate(request1);
        double p = result.noul("hazard").noul();
        if (p >= blockThreshold) {
            return new Verdict(p, Action.BLOCK, "probability " + p + " >= block threshold " + blockThreshold);
        }
        if (p >= reviewThreshold) {
            return new Verdict(p, Action.REVIEW, "probability " + p + " >= review threshold " + reviewThreshold);
        }
        return new Verdict(p, Action.ALLOW, "probability " + p + " below review threshold");
    }
}
