package com.careercoach.coach;

/** Per-skill exp the evaluator proposes for one skill of the graded task. */
public record SkillBreakdown(String skillKey, long exp) {
}
