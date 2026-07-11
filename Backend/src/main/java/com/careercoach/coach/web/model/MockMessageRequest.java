package com.careercoach.coach.web.model;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/mock/{sessionId}/messages} — the user's turn in the interview. */
public record MockMessageRequest(@NotBlank String message) {
}
