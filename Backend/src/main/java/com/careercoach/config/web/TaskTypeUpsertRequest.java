package com.careercoach.config.web;

import com.careercoach.config.domain.VerificationMethod;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record TaskTypeUpsertRequest(
        @NotBlank String displayName,
        @NotNull VerificationMethod verificationMethod,
        @PositiveOrZero int expBase,
        boolean expScaleByScore,
        boolean requiresArtifact) {
}
