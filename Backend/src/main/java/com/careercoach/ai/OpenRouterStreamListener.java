package com.careercoach.ai;

/**
 * Callback sink for {@link OpenRouterClient#stream} — the streaming counterpart of
 * {@link OpenRouterCompletion}. The adapter invokes {@link #onToken} for each fragment
 * as it arrives (never buffering the whole reply), then exactly one terminal signal:
 * {@link #onComplete} on success or {@link #onError} on failure — including a failure
 * that occurs <b>mid-stream</b>, so callers can surface it instead of silently truncating.
 */
public interface OpenRouterStreamListener {

    /** A single response fragment (token or chunk) as it streams in. */
    void onToken(String token);

    /** The stream failed (possibly after some tokens were already delivered). */
    void onError(Throwable error);

    /** The stream finished normally. */
    void onComplete();
}
