package com.careercoach.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.repository.TaskRepository;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.config.web.TaskTypeUpsertRequest;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.coach.domain.MockMessage;
import com.careercoach.coach.domain.MockSession;
import com.careercoach.coach.domain.MockSessionState;
import com.careercoach.coach.repository.MockMessageRepository;
import com.careercoach.coach.repository.MockSessionRepository;

/**
 * Service test for {@link MockTranscriptWriter} (issue-6) against a real Postgres: each
 * append persists one {@link MockMessage} with the next sequential {@code seq}, so the
 * transcript is written incrementally and a partial session (only the user turn saved)
 * keeps exactly what was already exchanged. Red phase: {@code append} throws
 * {@code UnsupportedOperationException}.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false"
})
class MockTranscriptWriterTest {

    @Autowired private MockTranscriptWriter writer;
    @Autowired private MockSessionRepository mockSessionRepository;
    @Autowired private MockMessageRepository mockMessageRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private TaskTypeDefinitionService taskTypeDefinitionService;

    private Long sessionId;

    @BeforeEach
    void setUp() {
        mockMessageRepository.deleteAll();
        mockSessionRepository.deleteAll();
        taskRepository.deleteAll();
        goalRepository.deleteAll();

        taskTypeDefinitionService.upsert("MOCK",
                new TaskTypeUpsertRequest("Mock interview", VerificationMethod.AI_DIALOG, 60, false, false));
        Goal goal = goalRepository.save(Goal.builder()
                .parentId(null).kind(GoalKind.STRATEGIC).title("Goal").description("desc")
                .state(GoalState.ACTIVE).createdBy(GoalCreatedBy.USER).orderIndex(0).expEarned(0L).build());
        Task task = taskRepository.save(Task.builder()
                .goalId(goal.getId()).typeKey("MOCK").title("Mock").description("desc")
                .state(TaskState.IN_PROGRESS).expAwarded(0L).build());
        MockSession session = mockSessionRepository.save(MockSession.builder()
                .taskId(task.getId()).state(MockSessionState.ACTIVE).build());
        sessionId = session.getId();
    }

    @Test
    void append_shouldStartSeqAtZeroAndIncrement() {
        // Act — three turns appended one after another
        writer.append(sessionId, "coach", "Question?");
        writer.append(sessionId, "user", "Answer.");
        writer.append(sessionId, "coach", "Follow-up?");

        // Assert — persisted incrementally with rising seq, in insertion order
        List<MockMessage> transcript = mockMessageRepository.findBySessionIdOrderBySeqAsc(sessionId);
        assertThat(transcript).extracting(MockMessage::getSeq).containsExactly(0, 1, 2);
        assertThat(transcript).extracting(MockMessage::getRole).containsExactly("coach", "user", "coach");
        assertThat(transcript).extracting(MockMessage::getContent)
                .containsExactly("Question?", "Answer.", "Follow-up?");
    }

    @Test
    void append_shouldPersistPartialTranscript_whenOnlyUserTurnSaved() {
        // Act — a crash after the user turn but before the coach reply: only one append happened
        writer.append(sessionId, "user", "A lonely answer.");

        // Assert — the already-written message survives intact (crash-resilient)
        List<MockMessage> transcript = mockMessageRepository.findBySessionIdOrderBySeqAsc(sessionId);
        assertThat(transcript).singleElement().satisfies(m -> {
            assertThat(m.getRole()).isEqualTo("user");
            assertThat(m.getContent()).isEqualTo("A lonely answer.");
            assertThat(m.getSeq()).isZero();
        });
    }
}
