package com.careercoach.coach.domain;

/**
 * A sub-goal proposed by DECOMPOSE. Persisted immediately as a {@code PROPOSED}
 * goal (it requires user acceptance), so {@code id} references the saved row.
 */
public record ProposedGoal(Long id, String title, String description) {
}
