package com.careercoach.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.coach.web.model.ChatTurn;
import com.careercoach.coach.web.model.CoachMessageRequest;

/**
 * Unit tests for {@link CoachChatService} (issue-3): the chat prompt is built through the
 * {@link ContextAssembler} (so advice is grounded, not generic) plus the current conversation
 * history, and {@code streamReply} hands that prompt to the OpenRouter streaming port. A
 * synchronous executor makes the async streaming deterministic. Red phase: both methods throw.
 */
class CoachChatServiceTest {

    private final ContextAssembler assembler = mock(ContextAssembler.class);
    private final OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
    private final Executor syncExecutor = Runnable::run;
    private final CoachChatService service =
            new CoachChatService(assembler, openRouterClient, syncExecutor);

    @Test
    void buildPrompt_shouldUseAssembledContextHistoryAndMessage() {
        // Arrange
        when(assembler.assemble(42L)).thenReturn("ASSEMBLED-CONTEXT");
        CoachMessageRequest request = new CoachMessageRequest(42L, "what should I do next?",
                List.of(new ChatTurn("user", "I want to switch to backend"),
                        new ChatTurn("coach", "Great, let us plan it")));

        // Act
        String prompt = service.buildPrompt(request);

        // Assert — grounded context + prior turns + the new message all present
        assertThat(prompt)
                .contains("ASSEMBLED-CONTEXT")
                .contains("I want to switch to backend")
                .contains("Great, let us plan it")
                .contains("what should I do next?");
        verify(assembler).assemble(42L);
    }

    @Test
    void buildPrompt_shouldTolerateNullHistory() {
        // Arrange
        when(assembler.assemble(1L)).thenReturn("CTX");
        CoachMessageRequest request = new CoachMessageRequest(1L, "hello", null);

        // Act
        String prompt = service.buildPrompt(request);

        // Assert
        assertThat(prompt).contains("CTX").contains("hello");
    }

    @Test
    void streamReply_shouldStreamBuiltPromptThroughPort_andReturnEmitter() {
        // Arrange
        when(assembler.assemble(7L)).thenReturn("CONTEXT-BLOCK");
        CoachMessageRequest request = new CoachMessageRequest(7L, "am I on track?", List.of());

        // Act
        SseEmitter emitter = service.streamReply(request);

        // Assert — a live emitter is returned and the port is driven with the assembled prompt
        assertThat(emitter).isNotNull();
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openRouterClient).stream(promptCaptor.capture(), any());
        assertThat(promptCaptor.getValue()).contains("CONTEXT-BLOCK").contains("am I on track?");
    }

    @Test
    void streamReply_shouldAssembleContextForRequestedGoal() {
        // Arrange
        when(assembler.assemble(99L)).thenReturn("CTX");
        CoachMessageRequest request = new CoachMessageRequest(99L, "hi", List.of());

        // Act
        service.streamReply(request);

        // Assert — context is anchored to the goal in the request
        verify(assembler).assemble(eq(99L));
        verify(openRouterClient).stream(anyString(), any());
    }
}
