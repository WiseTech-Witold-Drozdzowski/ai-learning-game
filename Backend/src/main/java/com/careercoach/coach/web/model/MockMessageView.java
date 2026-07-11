package com.careercoach.coach.web.model;

import java.time.Instant;

import com.careercoach.coach.domain.MockMessage;

/** Read projection of one transcript entry, for reviewing a session after the fact. */
public record MockMessageView(String role, String content, int seq, Instant createdAt) {

    public static MockMessageView from(MockMessage message) {
        return new MockMessageView(message.getRole(), message.getContent(), message.getSeq(),
                message.getCreatedAt());
    }
}
