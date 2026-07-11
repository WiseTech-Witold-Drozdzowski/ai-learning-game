package com.careercoach.coach.service;

import java.io.IOException;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.ai.OpenRouterStreamListener;

/**
 * Bridges an OpenRouter token stream onto an {@link SseEmitter} (reusing the issue-3
 * seam) while accumulating the coach reply so the whole message can be persisted as
 * one transcript entry once the stream completes. Each fragment is relayed as a
 * {@code token} SSE event the instant it arrives; a mid-stream failure becomes an
 * {@code error} event (never a silent truncation); completion persists the coach turn
 * and closes the emitter.
 */
class MockStreamListener implements OpenRouterStreamListener {

    private final SseEmitter emitter;
    private final MockTranscriptWriter transcriptWriter;
    private final Long sessionId;
    private final StringBuilder reply = new StringBuilder();

    MockStreamListener(SseEmitter emitter, MockTranscriptWriter transcriptWriter, Long sessionId) {
        this.emitter = emitter;
        this.transcriptWriter = transcriptWriter;
        this.sessionId = sessionId;
    }

    @Override
    public void onToken(String token) {
        reply.append(token);
        try {
            emitter.send(SseEmitter.event().name("token").data(token));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
        }
    }

    @Override
    public void onError(Throwable error) {
        String message = error.getMessage() == null ? "stream error" : error.getMessage();
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException ex) {
            // Best effort — the client disconnected; the emitter is closed below regardless.
        } finally {
            emitter.complete();
        }
    }

    @Override
    public void onComplete() {
        // Persist the coach turn incrementally, only after the reply has fully streamed.
        transcriptWriter.append(sessionId, "coach", reply.toString());
        emitter.complete();
    }
}
