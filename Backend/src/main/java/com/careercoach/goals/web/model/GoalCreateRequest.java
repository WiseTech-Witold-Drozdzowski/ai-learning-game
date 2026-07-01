package com.careercoach.goals.web.model;

import jakarta.validation.constraints.NotBlank;

public record GoalCreateRequest(
        @NotBlank String title,
        String description,
        Long parentId
) {
}
