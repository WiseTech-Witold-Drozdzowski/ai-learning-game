package com.careercoach.coach;

import com.careercoach.jobs.JobPayload;

/** Typed input of a PLANNING job (BACKEND_DESIGN §4): which goal, which mode. */
public record PlanningInput(Long goalId, PlanningMode mode) implements JobPayload {
}
