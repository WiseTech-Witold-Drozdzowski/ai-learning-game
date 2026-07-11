package com.careercoach.coach.web.model;

import com.careercoach.coach.domain.PlanningMode;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/goals/{id}/plan}. */
public record PlanRequest(@NotNull PlanningMode mode) {
}
