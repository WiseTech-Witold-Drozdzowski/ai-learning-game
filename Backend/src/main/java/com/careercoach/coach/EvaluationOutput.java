package com.careercoach.coach;

import java.util.List;

import com.careercoach.jobs.JobPayload;

/**
 * Typed output of an EVALUATION job (BACKEND_DESIGN §4). {@code expProposed} is
 * only a <em>proposal</em> — {@link com.careercoach.gamification.service.GamificationService}
 * clamps it on award. {@code skillBreakdown} is the per-skill split; {@code passed}
 * drives the terminal task state.
 */
public record EvaluationOutput(
        int score,
        long expProposed,
        List<SkillBreakdown> skillBreakdown,
        String feedback,
        boolean passed) implements JobPayload {
}
