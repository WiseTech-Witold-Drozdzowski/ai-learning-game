package com.careercoach.coach.web.model;

import java.time.Instant;
import java.util.List;

import com.careercoach.coach.domain.MockMessage;
import com.careercoach.coach.domain.MockSession;
import com.careercoach.coach.domain.MockSessionState;

/** Read projection of a mock session plus its full transcript (issue-6 review view). */
public record MockSessionView(
        Long id,
        Long taskId,
        MockSessionState state,
        Integer score,
        Instant startedAt,
        Instant finishedAt,
        List<MockMessageView> messages) {

    public static MockSessionView from(MockSession session, List<MockMessage> messages) {
        return new MockSessionView(
                session.getId(),
                session.getTaskId(),
                session.getState(),
                session.getScore(),
                session.getStartedAt(),
                session.getFinishedAt(),
                messages.stream().map(MockMessageView::from).toList());
    }
}
