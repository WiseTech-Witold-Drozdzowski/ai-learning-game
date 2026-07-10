package com.careercoach.tasks.service;

import java.util.List;

import com.careercoach.tasks.domain.Task;

/**
 * Port (owned by {@code tasks}) for launching AI verification of a submitted task.
 * Implemented in {@code coach} ({@code EvaluationLauncher}) — inverted here so
 * {@link TaskService} depends only on its own module and the {@code tasks → coach}
 * constructor cycle is avoided ({@code coach → tasks} already exists).
 */
public interface AiVerificationLauncher {

    /**
     * Enqueue an EVALUATION job for {@code task} and return the created job id
     * (stored on the task as {@code verificationJobId}). {@code artifact} feeds the
     * artifact-review path; {@code answers} feed the AUTO_QUIZ path — a method uses
     * the one it needs (either may be {@code null}).
     */
    Long launchEvaluation(Task task, String artifact, List<String> answers);
}
