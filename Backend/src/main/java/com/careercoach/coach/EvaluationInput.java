package com.careercoach.coach;

import java.util.List;

import com.careercoach.jobs.JobPayload;

/**
 * Typed input of an EVALUATION job (BACKEND_DESIGN §4): which task and type to
 * grade, plus the material to grade — an {@code artifact} (AI_ARTIFACT_REVIEW) or
 * {@code answers} (AUTO_QUIZ, issue-5). Both are nullable; a method uses the one
 * it needs.
 */
public record EvaluationInput(Long taskId, String typeKey, String artifact, List<String> answers)
        implements JobPayload {
}
