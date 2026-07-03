package com.careercoach.gamification.web.model;

import java.time.Instant;

public record ExpEventView(Long sourceTaskId, Long attemptId, long amount, String reason, Instant createdAt) {
}
