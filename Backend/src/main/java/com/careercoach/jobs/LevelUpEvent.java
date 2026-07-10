package com.careercoach.jobs;

/**
 * SSE payload for a profile level-up (BACKEND_DESIGN §5 / §7). Emitted on the
 * global stream only when an award pushes the profile past a level threshold.
 */
public record LevelUpEvent(Long sourceTaskId, int newLevel) {
}
