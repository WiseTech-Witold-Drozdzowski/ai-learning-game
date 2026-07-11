package com.careercoach.coach.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import com.careercoach.coach.domain.MockMessage;
import com.careercoach.coach.repository.MockMessageRepository;

/**
 * Appends transcript entries to a session <em>incrementally</em> (issue-6): each call
 * persists one {@link MockMessage} in its own transaction with the next {@code seq}, so
 * a crash mid-session leaves every already-written message intact. Kept as a distinct
 * component (not a private method on {@link MockSessionService}) so the streaming
 * callback can persist the coach reply through a real Spring transactional proxy.
 */
@Component
@RequiredArgsConstructor
public class MockTranscriptWriter {

    private final MockMessageRepository mockMessageRepository;

    /** Persist one transcript entry for {@code sessionId} with the next sequential {@code seq}. */
    @Transactional
    public MockMessage append(Long sessionId, String role, String content) {
        int nextSeq = mockMessageRepository.findFirstBySessionIdOrderBySeqDesc(sessionId)
                .map(last -> last.getSeq() + 1)
                .orElse(0);
        MockMessage message = MockMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .seq(nextSeq)
                .build();
        return mockMessageRepository.save(message);
    }
}
