package com.careercoach.config.web;

import jakarta.validation.constraints.NotBlank;

public record SkillUpsertRequest(
        @NotBlank String displayName,
        @NotBlank String category) {
}
