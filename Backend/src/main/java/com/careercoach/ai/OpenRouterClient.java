package com.careercoach.ai;

/**
 * Port to OpenRouter's chat API (TECHNICAL_DESIGN §3, BACKEND_DESIGN §1).
 *
 * <p><b>Explicitly separate</b> from {@code ClaudeCliClient} — the two are NOT
 * hidden behind a shared {@code LlmClient} because they are different kinds of
 * thing (synchronous chat vs. autonomous tool-using agent). The only shared
 * contract is {@link com.careercoach.jobs.JobHandler}.
 *
 * <p>Synchronous request → response. Mocked in tests with deterministic JSON.
 */
public interface OpenRouterClient {

    /** Run a single chat completion for {@code prompt} and return the assistant content. */
    OpenRouterCompletion complete(String prompt);
}
