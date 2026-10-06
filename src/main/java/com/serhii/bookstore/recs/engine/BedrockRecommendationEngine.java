package com.serhii.bookstore.recs.engine;

import com.serhii.bookstore.catalog.domain.Book;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.BedrockRuntimeException;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Real Bedrock call — Nova Micro via Converse, forced tool use rather than
 * free-text-JSON-then-parse (Nova Micro's model card
 * lists "Structured outputs" as unsupported but "Client-side tool calling"
 * as supported — this is the reliable mechanism, not a style preference).
 *
 * <p><b>Model ID is the Geo (EU) inference profile, not the base model
 * ID</b> — confirmed against both {@code aws bedrock list-foundation-models}
 * (eu-central-1 shows {@code inferenceTypesSupported: ["INFERENCE_PROFILE"]}
 * only) and the model's own AWS doc page (no In-Region checkmark for
 * eu-central-1). Calling {@code amazon.nova-micro-v1:0} directly from this
 * region fails with {@code ValidationException: on-demand throughput isn't
 * supported}; IAM grants two ARNs for this
 * one model, not one.
 *
 * <p>Only active when {@code bookstore.recs.mode=bedrock} — see
 * {@link MockRecommendationEngine}'s javadoc for why this is a genuine
 * Spring conditional (bean never constructed elsewhere), not the §5.10
 * "unconditional {@code @Component}" IAM trap.
 *
 * <p>Owns its own {@link BedrockRuntimeClient} lifecycle the same way
 * {@code order.service.OrderEventPublisher} owns its {@code EventBridgeClient}
 * — one caller, no shared Holder/Factory indirection, client rebuilt fresh
 * on SnapStart restore (never resumes a frozen connection/credential
 * timer), no eager post-restore primer call (same rationale as
 * {@code OrderEventPublisher}: this function isn't SnapStart-published the
 * way the always-hot auth path is, and every failure here is already
 * caught and turned into a fallback, never a wrong result).
 */
@Component
@ConditionalOnProperty(name = "bookstore.recs.mode", havingValue = "bedrock")
public class BedrockRecommendationEngine implements RecommendationEngine, Resource {

    private static final Logger log = LoggerFactory.getLogger(BedrockRecommendationEngine.class);

    /** Geo (EU) cross-region inference profile — see class javadoc. */
    private static final String MODEL_ID = "eu.amazon.nova-micro-v1:0";
    private static final String TOOL_NAME = "recommend_books";
    private static final int MAX_TOKENS = 300;

    private final AtomicReference<BedrockRuntimeClient> client = new AtomicReference<>();

    @PostConstruct
    void init() {
        client.set(buildClient());
        Core.getGlobalContext().register(this);
    }

    /** Test-only: bypasses {@code @PostConstruct}/CRaC registration, injects a client directly. */
    void setClientForTesting(BedrockRuntimeClient client) {
        this.client.set(client);
    }

    @Override
    public List<String> recommend(List<String> purchasedBookNames, List<Book> candidateBooks) {
        ConverseRequest request = buildRequest(purchasedBookNames, candidateBooks);
        ConverseResponse response;
        try {
            response = client.get().converse(request);
        } catch (BedrockRuntimeException | SdkClientException e) {
            log.warn("Bedrock recommend call failed, errorType={}, errorMessage={}",
                    e.getClass().getSimpleName(), e.getMessage());
            throw new RecommendationEngineException("Bedrock InvokeModel failed", e);
        }
        return extractBookIds(response);
    }

    private static ConverseRequest buildRequest(List<String> purchasedBookNames, List<Book> candidateBooks) {
        StringBuilder userText = new StringBuilder("Previously purchased titles:\n");
        purchasedBookNames.forEach(name -> userText.append("- ").append(name).append('\n'));
        userText.append("\nCandidate books (bookId: title):\n");
        candidateBooks.forEach(book -> userText.append("- ").append(book.bookId()).append(": ").append(book.name()).append('\n'));

        String system = "You are a book recommendation assistant for an online bookstore. "
                + "Given a reader's previously purchased titles and a list of candidate books, "
                + "call the " + TOOL_NAME + " tool with up to " + MockRecommendationEngine.MAX_RECOMMENDATIONS
                + " bookId values chosen ONLY from the candidate list, picking titles that seem "
                + "thematically related to what the reader already bought based on their titles.";

        return ConverseRequest.builder()
                .modelId(MODEL_ID)
                .system(SystemContentBlock.builder().text(system).build())
                .messages(Message.builder()
                        .role(ConversationRole.USER)
                        .content(ContentBlock.builder().text(userText.toString()).build())
                        .build())
                .toolConfig(buildToolConfig())
                // Explicit and small — the Bedrock skill's Critical Warning: an unset maxTokens
                // defaults to the model's max and silently reserves far more quota than needed.
                // Nova Micro's own cap is 5K, but a tool-call response needs a fraction of that.
                .inferenceConfig(InferenceConfiguration.builder().maxTokens(MAX_TOKENS).build())
                .build();
    }

    private static ToolConfiguration buildToolConfig() {
        Document inputSchema = Document.mapBuilder()
                .putString("type", "object")
                .putMap("properties", properties -> properties
                        .putMap("bookIds", bookIds -> bookIds
                                .putString("type", "array")
                                .putMap("items", items -> items.putString("type", "string"))
                                .putString("description", "Chosen bookId values, from the candidate list only.")))
                .putList("required", required -> required.addString("bookIds"))
                .build();

        Tool tool = Tool.builder()
                .toolSpec(ToolSpecification.builder()
                        .name(TOOL_NAME)
                        .description("Selects book IDs to recommend, from the given candidate list only.")
                        .inputSchema(ToolInputSchema.builder().json(inputSchema).build())
                        .build())
                .build();

        return ToolConfiguration.builder()
                .tools(tool)
                // Forced, not "auto" — Nova Micro doesn't support structured outputs (see class
                // javadoc), so a forced tool choice is what actually guarantees a parseable
                // response instead of the model replying with plain text instead of calling the tool.
                .toolChoice(ToolChoice.builder().tool(SpecificToolChoice.builder().name(TOOL_NAME).build()).build())
                .build();
    }

    private List<String> extractBookIds(ConverseResponse response) {
        ToolUseBlock toolUse = response.output().message().content().stream()
                .map(ContentBlock::toolUse)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (toolUse == null) {
            log.warn("Bedrock recommend response had no toolUse block, stopReason={}", response.stopReasonAsString());
            throw new RecommendationEngineException("Bedrock response contained no toolUse block");
        }
        Document input = toolUse.input();
        Document bookIdsDocument = input != null && input.isMap() ? input.asMap().get("bookIds") : null;
        if (bookIdsDocument == null || !bookIdsDocument.isList()) {
            log.warn("Bedrock recommend tool input missing/malformed bookIds field, toolUseInput={}", input);
            throw new RecommendationEngineException("Bedrock tool response missing/malformed bookIds field");
        }
        return bookIdsDocument.asList().stream()
                .filter(Document::isString)
                .map(Document::asString)
                .toList();
    }

    @Override
    public void beforeCheckpoint(Context<? extends Resource> context) {
        BedrockRuntimeClient previous = client.get();
        if (previous != null) {
            previous.close();
        }
    }

    @Override
    public void afterRestore(Context<? extends Resource> context) {
        client.set(buildClient());
    }

    private static BedrockRuntimeClient buildClient() {
        return BedrockRuntimeClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
