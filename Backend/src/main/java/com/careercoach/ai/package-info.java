/**
 * Module {@code ai} — AI provider PORTS.
 *
 * <p>{@code OpenRouterClient} (chat/SSE) AND {@code ClaudeCliClient} (agent, later)
 * are explicitly separate — NOT hidden behind a shared {@code LlmClient}. The only
 * shared contract is {@link com.careercoach.jobs.JobHandler}. TECHNICAL_DESIGN §3.
 */
package com.careercoach.ai;
