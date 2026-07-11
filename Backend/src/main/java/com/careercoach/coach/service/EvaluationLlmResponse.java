package com.careercoach.coach.service;

import java.util.List;

/**
 * Parse target for the evaluator LLM reply (mocked in tests). The model returns a
 * {@code score}, a {@code passed} verdict, free-text {@code feedback} and a
 * per-skill exp proposal in {@code skills}. Total proposed exp is derived by
 * summing {@link SkillItem#exp()} — the model never sets a total directly.
 */
public record EvaluationLlmResponse(int score, boolean passed, String feedback, List<SkillItem> skills) {

    public record SkillItem(String skillKey, long exp) {
    }
}
