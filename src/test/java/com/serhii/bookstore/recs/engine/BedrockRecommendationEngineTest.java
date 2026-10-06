package com.serhii.bookstore.recs.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.serhii.bookstore.catalog.domain.Book;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.Message;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * Mocks {@link BedrockRuntimeClient} directly — no real Bedrock call, ever,
 * from a test. Covers: the request is built with the
 * forced tool choice and an explicit maxTokens, a successful tool-use
 * response is parsed, and every failure mode (thrown AWS exception,
 * missing toolUse block, malformed tool input) surfaces as
 * {@link RecommendationEngineException} rather than propagating the raw
 * cause or silently returning an empty/wrong result.
 */
@ExtendWith(MockitoExtension.class)
class BedrockRecommendationEngineTest {

    @Mock
    private BedrockRuntimeClient client;

    private BedrockRecommendationEngine engine;

    private static Book book(String id) {
        return new Book(id, "Book " + id, 1000, 5, "photo.jpg", true, Instant.now());
    }

    private static ConverseResponse responseWithToolUse(Document input) {
        ToolUseBlock toolUse = ToolUseBlock.builder().toolUseId("t1").name("recommend_books").input(input).build();
        Message message = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.builder().toolUse(toolUse).build())
                .build();
        return ConverseResponse.builder()
                .output(ConverseOutput.builder().message(message).build())
                .stopReason(StopReason.TOOL_USE)
                .build();
    }

    @BeforeEach
    void setUp() {
        engine = new BedrockRecommendationEngine();
        engine.setClientForTesting(client);
    }

    @Test
    void parsesRecommendedBookIdsFromTheToolUseResponse() {
        Document input = Document.mapBuilder()
                .putList("bookIds", ids -> ids.addString("book-1").addString("book-2"))
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(responseWithToolUse(input));

        List<String> result = engine.recommend(List.of("Effective Java"), List.of(book("book-1"), book("book-2")));

        assertThat(result).containsExactly("book-1", "book-2");
    }

    @Test
    void requestForcesTheToolChoiceAndSetsMaxTokensExplicitly() {
        Document input = Document.mapBuilder().putList("bookIds", ids -> ids.addString("book-1")).build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(responseWithToolUse(input));

        engine.recommend(List.of("Effective Java"), List.of(book("book-1")));

        ArgumentCaptor<ConverseRequest> captor = ArgumentCaptor.forClass(ConverseRequest.class);
        verify(client).converse(captor.capture());
        ConverseRequest request = captor.getValue();
        assertThat(request.modelId()).isEqualTo("eu.amazon.nova-micro-v1:0");
        assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo("recommend_books");
        assertThat(request.inferenceConfig().maxTokens()).isNotNull();
    }

    @Test
    void wrapsAThrownBedrockExceptionAsRecommendationEngineException() {
        when(client.converse(any(ConverseRequest.class)))
                .thenThrow(ThrottlingException.builder().message("rate exceeded").build());

        assertThatThrownBy(() -> engine.recommend(List.of("x"), List.of(book("book-1"))))
                .isInstanceOf(RecommendationEngineException.class)
                .hasCauseInstanceOf(ThrottlingException.class);
    }

    @Test
    void throwsWhenResponseHasNoToolUseBlock() {
        Message textOnlyMessage = Message.builder()
                .role(ConversationRole.ASSISTANT)
                .content(ContentBlock.builder().text("I don't want to call a tool").build())
                .build();
        ConverseResponse response = ConverseResponse.builder()
                .output(ConverseOutput.builder().message(textOnlyMessage).build())
                .stopReason(StopReason.END_TURN)
                .build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(response);

        assertThatThrownBy(() -> engine.recommend(List.of("x"), List.of(book("book-1"))))
                .isInstanceOf(RecommendationEngineException.class);
    }

    @Test
    void throwsWhenToolInputIsMissingTheBookIdsField() {
        Document input = Document.mapBuilder().putString("somethingElse", "oops").build();
        when(client.converse(any(ConverseRequest.class))).thenReturn(responseWithToolUse(input));

        assertThatThrownBy(() -> engine.recommend(List.of("x"), List.of(book("book-1"))))
                .isInstanceOf(RecommendationEngineException.class);
    }
}
