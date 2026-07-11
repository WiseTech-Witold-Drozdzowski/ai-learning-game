package com.careercoach.ai;

/**
 * Port to OpenRouter's chat API (TECHNICAL_DESIGN §3, BACKEND_DESIGN §1).
 *
 * <p><b>Explicitly separate</b> from {@code ClaudeCliClient} — the two are NOT
 * hidden behind a shared {@code LlmClient} because they are different kinds of
 * thing (synchronous chat vs. autonomous tool-using agent). The only shared
 * contract is {@link com.careercoach.jobs.JobHandler}.
 *
 * <p>Synchronous request → response, plus a streaming mode (SSE) that pushes the
 * assistant reply fragment-by-fragment. Mocked in tests: the stub returns
 * deterministic JSON for {@link #complete} and emits a fixed sequence of fragments
 * for {@link #stream}.
 */
public interface OpenRouterClient {

    /** Run a single chat completion for {@code prompt} and return the assistant content. */
    OpenRouterCompletion complete(String prompt);

    /**
     * Stream the assistant reply for {@code prompt}, invoking {@code listener} for each
     * fragment as it arrives (no whole-response buffering) and then a single terminal
     * {@link OpenRouterStreamListener#onComplete}/{@link OpenRouterStreamListener#onError}.
     * This is the seam re-used to pass OpenRouter SSE through Spring to the client.
     */
    void stream(String prompt, OpenRouterStreamListener listener);
}
