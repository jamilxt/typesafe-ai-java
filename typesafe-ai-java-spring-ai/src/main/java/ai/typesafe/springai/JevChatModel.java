package ai.typesafe.springai;

import ai.typesafe.TypeSafeClient;
import ai.typesafe.model.ChoiceAnswer;
import ai.typesafe.model.EvaluationRequest;
import ai.typesafe.model.NoulAnswer;
import ai.typesafe.model.ScoreAnswer;
import ai.typesafe.model.SystemOneResult;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Exposes Jev as a Spring AI {@link ChatModel} so it can drop into any
 * existing ChatClient pipeline, e.g. to swap "the LLM doing triage" for Jev
 * and compare cost and latency without changing calling code.
 *
 * <p>Mapping: the last {@link UserMessage} is the state; the remaining
 * messages are concatenated (newline-joined) and sent as the instructions of
 * a single noul question named {@code decision}. The winning probability is
 * rendered as text (the probability formatted to four decimals) in a single
 * Generation, and the full typed result is attached to the response metadata
 * under {@link #RESULT_METADATA_KEY} for code that needs probabilities and
 * confidence.</p>
 */
public final class JevChatModel implements ChatModel {

    /** Metadata key under which the raw {@link SystemOneResult} is attached to every {@link ChatResponse}. */
    public static final String RESULT_METADATA_KEY = "jev_result";

    private final TypeSafeClient client;

    /** @deprecated racy instance-level holder; retained only for the deprecated {@link #lastResult()} accessor. */
    @Deprecated
    private volatile SystemOneResult lastResultHolder;

    public JevChatModel(TypeSafeClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    /**
     * The raw result of the most recent call, for probability-level access.
     *
     * @deprecated prefer reading {@link #RESULT_METADATA_KEY} from the
     *     {@link ChatResponse} metadata: {@code response.getMetadata().get(JevChatModel.RESULT_METADATA_KEY)}.
     *     This instance-level accessor races under concurrent calls and will be
     *     removed in a future release.
     */
    @Deprecated
    public Optional<SystemOneResult> lastResult() {
        return lastResultHolder == null ? Optional.empty() : Optional.of(lastResultHolder);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        List<Message> messages = prompt.getInstructions();
        if (messages.isEmpty()) {
            throw new IllegalArgumentException("JevChatModel expects at least one message as the state");
        }
        String state = messages.get(messages.size() - 1).getText();
        String instructions = messages.size() > 1
                ? messages.subList(0, messages.size() - 1).stream()
                        .map(Message::getText)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("\n"))
                : "Does the state satisfy the user's implicit decision request?";

        var builder = EvaluationRequest.of(state)
                .noul("decision", instructions);
        if (prompt.getOptions() != null && prompt.getOptions().getModel() != null) {
            builder.model(prompt.getOptions().getModel());
        }

        SystemOneResult result = client.evaluate(builder.build());
        this.lastResultHolder = result;
        double probability = result.noul("decision").noul();
        String text = String.format("%.4f", probability);
        ChatResponseMetadata metadata = ChatResponseMetadata.builder()
                .keyValue(RESULT_METADATA_KEY, result)
                .build();
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))), metadata);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return ChatOptions.builder().model(TypeSafeClient.MODEL_JEV_LATEST).build();
    }
}
