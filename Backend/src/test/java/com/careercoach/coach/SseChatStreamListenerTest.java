package com.careercoach.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.DataWithMediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

/**
 * Unit tests for {@link SseChatStreamListener} (issue-3): each fragment is relayed as a
 * {@code token} SSE event in arrival order, a mid-stream failure as an {@code error}
 * event (not a silent truncation), and completion closes the emitter. Uses a recording
 * {@link SseEmitter} subclass so sends are observed without a servlet response.
 * Red phase: every listener callback throws.
 */
class SseChatStreamListenerTest {

    /** Records the rendered SSE frames and terminal signals instead of writing to a response. */
    static class RecordingSseEmitter extends SseEmitter {
        final List<String> frames = new ArrayList<>();
        boolean completed;

        @Override
        public void send(SseEventBuilder builder) {
            StringBuilder sb = new StringBuilder();
            for (DataWithMediaType part : builder.build()) {
                sb.append(part.getData());
            }
            frames.add(sb.toString());
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    @Test
    void onToken_shouldRelayFragmentsAsTokenEvents_inOrder() {
        // Arrange
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SseChatStreamListener listener = new SseChatStreamListener(emitter);

        // Act — fragments arrive one at a time
        listener.onToken("Hel");
        listener.onToken("lo");
        listener.onComplete();

        // Assert — two token frames, in order, then the emitter is closed
        assertThat(emitter.frames).hasSize(2);
        assertThat(emitter.frames.get(0)).contains("event:token").contains("Hel");
        assertThat(emitter.frames.get(1)).contains("event:token").contains("lo");
        assertThat(emitter.frames.get(0).indexOf("Hel"))
                .isLessThan(emitter.frames.get(1).indexOf("lo") + emitter.frames.get(0).length());
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void onError_shouldEmitErrorEventOnSse_andCloseEmitter() {
        // Arrange — one token already delivered, then the port fails mid-stream
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SseChatStreamListener listener = new SseChatStreamListener(emitter);

        // Act
        listener.onToken("partial");
        listener.onError(new RuntimeException("port down"));

        // Assert — the failure is surfaced as an SSE error frame after the token, not swallowed
        assertThat(emitter.frames).hasSize(2);
        assertThat(emitter.frames.get(0)).contains("event:token").contains("partial");
        assertThat(emitter.frames.get(1)).contains("event:error").contains("port down");
        assertThat(emitter.completed).isTrue();
    }

    @Test
    void onComplete_shouldCloseEmitter_withoutFurtherFrames() {
        // Arrange
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        SseChatStreamListener listener = new SseChatStreamListener(emitter);

        // Act
        listener.onComplete();

        // Assert
        assertThat(emitter.frames).isEmpty();
        assertThat(emitter.completed).isTrue();
    }
}
