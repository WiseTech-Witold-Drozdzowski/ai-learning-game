package com.careercoach.coach.service;

import java.util.List;

/**
 * Parse target for the evaluator LLM reply (mocked in tests). The model returns a
 * {@code score}, a {@code passed} verdict, free-text {@code feedback} and a
 * per-skill exp proposal in {@code skills}. Total proposed exp is derived by
 * summing {@link SkillItem#exp()} — the model never sets a total directly.
 * {@code coachNotes} carries the autonomous memory operations the coach chose to make
 * during evaluation (issue-7) — a distillate of what it learned, never the raw material.
 */
public record EvaluationLlmResponse(int score, boolean passed, String feedback, List<SkillItem> skills,
                                    List<CoachNoteOp> coachNotes) {

    public record SkillItem(String skillKey, long exp) {
    }
}
