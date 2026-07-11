package com.careercoach.coach.web.model;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/coach/messages}. {@code goalId} anchors the assembled
 * coach context (so advice is not generic); {@code history} carries the current
 * strategic conversation (client-held — the chat is synchronous, non-Job, ephemeral).
 */
public record CoachMessageRequest(
        @NotNull Long goalId,
        @NotBlank String message,
        List<ChatTurn> history) {
}
