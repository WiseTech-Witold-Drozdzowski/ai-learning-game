package com.careercoach.coach.service;

import com.careercoach.jobs.JobPayload;
import com.careercoach.coach.domain.PlanningMode;

/** Typed input of a PLANNING job (BACKEND_DESIGN §4): which goal, which mode. */
public record PlanningInput(Long goalId, PlanningMode mode) implements JobPayload {
}
