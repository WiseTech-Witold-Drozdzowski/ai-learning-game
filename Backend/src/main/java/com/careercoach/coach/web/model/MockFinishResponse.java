package com.careercoach.coach.web.model;

/** Response of {@code POST /api/mock/{sessionId}/finish} — the enqueued EVALUATION job id. */
public record MockFinishResponse(Long jobId) {
}
