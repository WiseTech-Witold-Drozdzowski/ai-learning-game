package com.careercoach.coach.web;

import com.careercoach.coach.PlanningMode;

import jakarta.validation.constraints.NotNull;

/** Body of {@code POST /api/goals/{id}/plan}. */
public record PlanRequest(@NotNull PlanningMode mode) {
}
