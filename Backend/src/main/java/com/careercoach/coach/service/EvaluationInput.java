package com.careercoach.coach.service;

import java.util.List;

import com.careercoach.jobs.JobPayload;

/**
 * Typed input of an EVALUATION job (BACKEND_DESIGN §4): which task and type to
 * grade, plus the material to grade — an {@code artifact} (AI_ARTIFACT_REVIEW),
 * {@code answers} (AUTO_QUIZ, issue-5) or a {@code mockSessionId} whose transcript is
 * graded (AI_DIALOG, issue-6). All three are nullable; a method uses the one it needs.
 */
public record EvaluationInput(Long taskId, String typeKey, String artifact, List<String> answers,
                              Long mockSessionId) implements JobPayload {

    /** Convenience for the non-mock paths (artifact / answers) — no mock session. */
    public EvaluationInput(Long taskId, String typeKey, String artifact, List<String> answers) {
        this(taskId, typeKey, artifact, answers, null);
    }
}
