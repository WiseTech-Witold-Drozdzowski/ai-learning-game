package com.careercoach.jobs;

/**
 * SSE payload for an exp accrual (BACKEND_DESIGN §5 / §7). Emitted on the global
 * stream after {@code GamificationService.award} grants exp for {@code sourceTaskId}.
 */
public record ExpGainEvent(Long sourceTaskId, long granted, long totalExp, int level) {
}
