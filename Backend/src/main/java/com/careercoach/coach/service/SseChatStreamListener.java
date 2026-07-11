package com.careercoach.coach.service;

import java.io.IOException;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.ai.OpenRouterStreamListener;

/**
 * Bridges an OpenRouter token stream onto an {@link SseEmitter}: each fragment is
 * relayed as a {@code token} SSE event the instant it arrives, a mid-stream failure
 * as an {@code error} event (never a silent truncation), and completion closes the
 * emitter.
 */
class SseChatStreamListener implements OpenRouterStreamListener {

    private final SseEmitter emitter;

    SseChatStreamListener(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onToken(String token) {
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
        emitter.complete();
    }
}
