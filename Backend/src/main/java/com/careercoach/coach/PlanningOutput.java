package com.careercoach.coach;

import java.util.List;

import com.careercoach.jobs.JobPayload;

/**
 * Typed output of a PLANNING job (BACKEND_DESIGN §4). Exactly one list is
 * populated per {@link PlanningMode}: {@code proposedGoals} for DECOMPOSE,
 * {@code proposedTasks} for GENERATE_TASKS.
 */
public record PlanningOutput(List<ProposedGoal> proposedGoals, List<ProposedTask> proposedTasks)
        implements JobPayload {
}
