package com.careercoach.coach;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.coach.web.ChatTurn;
import com.careercoach.coach.web.CoachMessageRequest;

import lombok.RequiredArgsConstructor;

/**
 * Strategic coach chat (issue-3): builds the chat prompt via the {@link ContextAssembler}
 * (so advice is grounded, not generic) plus the current conversation history, then streams
 * the OpenRouter reply through Spring to the client token-by-token via an {@link SseEmitter}.
 */
@Service
@RequiredArgsConstructor
public class CoachChatService {

    static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;

    private final ContextAssembler contextAssembler;
    private final OpenRouterClient openRouterClient;
    @Qualifier("coachChatExecutor")
    private final Executor coachChatExecutor;

    /** Start streaming the coach reply for {@code request}; returns the live SSE emitter. */
    public SseEmitter streamReply(CoachMessageRequest request) {
        String prompt = buildPrompt(request);
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        SseChatStreamListener listener = new SseChatStreamListener(emitter);
        coachChatExecutor.execute(() -> {
            try {
                openRouterClient.stream(prompt, listener);
            } catch (RuntimeException ex) {
                // A synchronous failure from the port is surfaced on the SSE stream too.
                listener.onError(ex);
            }
        });
        return emitter;
    }

    /** Build the chat prompt: assembled context + rendered history + the new user message. */
    String buildPrompt(CoachMessageRequest request) {
        return """
                %s

                ## Conversation
                %s
                user: %s
                coach:"""
                .formatted(
                        contextAssembler.assemble(request.goalId()),
                        buildConversation(request.history()),
                        request.message());
    }

    private String buildConversation(List<ChatTurn> history) {
        if (history == null) {
            return "";
        }
        return history.stream()
                .map(turn -> turn.role() + ": " + turn.content())
                .collect(Collectors.joining("\n"));
    }
}
