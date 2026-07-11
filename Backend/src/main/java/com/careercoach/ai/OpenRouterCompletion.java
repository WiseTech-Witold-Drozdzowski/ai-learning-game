package com.careercoach.ai;

/**
 * Structured result of a single OpenRouter chat completion — the assistant
 * message {@code content} (expected to be JSON when the caller asked for it).
 * Callers parse the content into their own domain shapes; the {@code ai} module
 * stays agnostic of what the text means.
 */
public record OpenRouterCompletion(String content) {
}
