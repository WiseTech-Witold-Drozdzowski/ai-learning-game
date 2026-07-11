package com.careercoach.coach.domain;

import java.util.List;

/**
 * A task proposed by GENERATE_TASKS. NOT persisted by the job — a {@code Task}
 * has no PROPOSED state, so a proposal lives in the job output until accepted,
 * at which point {@link PlanningService#acceptTaskProposals} creates a real
 * {@code TODO} task.
 */
public record ProposedTask(String title, String description, String typeKey, List<String> skillKeys) {
}
