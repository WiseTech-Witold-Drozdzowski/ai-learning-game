package com.careercoach.coach.service;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobService;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.service.TaskService;

import lombok.RequiredArgsConstructor;
import com.careercoach.coach.domain.MockMessage;
import com.careercoach.coach.domain.MockSession;
import com.careercoach.coach.domain.MockSessionState;
import com.careercoach.coach.domain.exception.MockSessionNotFoundException;
import com.careercoach.coach.repository.MockMessageRepository;
import com.careercoach.coach.repository.MockSessionRepository;

/**
 * Orchestrates a mock-interview session (issue-6, BACKEND_DESIGN §2.6): opens a session
 * and streams the coach's opening question, streams each conversational turn while
 * persisting the transcript incrementally, and finishes the session by enqueuing an
 * {@code AI_DIALOG} EVALUATION job that grades the transcript. The transcript is
 * readable for review but never enters the {@link ContextAssembler}.
 */
@Service
@RequiredArgsConstructor
public class MockSessionService {

    static final long STREAM_TIMEOUT_MS = 5 * 60 * 1000L;

    private final MockSessionRepository mockSessionRepository;
    private final MockMessageRepository mockMessageRepository;
    private final MockTranscriptWriter transcriptWriter;
    private final ContextAssembler contextAssembler;
    private final OpenRouterClient openRouterClient;
    private final JobService jobService;
    private final TaskService taskService;
    @Qualifier("coachChatExecutor")
    private final Executor coachChatExecutor;

    /**
     * Create an {@code ACTIVE} session for {@code taskId} and open the SSE stream: emit a
     * {@code session} event carrying the new session id (so the client knows where to POST
     * turns), then stream the coach's opening question token-by-token and persist it.
     */
    public SseEmitter start(Long taskId) {
        Task task = taskService.get(taskId);
        MockSession session = mockSessionRepository.save(MockSession.builder()
                .taskId(taskId)
                .state(MockSessionState.ACTIVE)
                .build());

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        try {
            // The client learns where to POST its turns before the question streams in.
            emitter.send(SseEmitter.event().name("session").data(session.getId()));
        } catch (IOException ex) {
            emitter.completeWithError(ex);
            return emitter;
        }
        streamCoachTurn(emitter, session.getId(), buildOpeningPrompt(task));
        return emitter;
    }

    /**
     * One conversational turn: persist the user's message incrementally, then stream the
     * coach's reply token-by-token, persisting it as the next transcript entry on completion.
     */
    public SseEmitter respond(Long sessionId, String userMessage) {
        MockSession session = findOrThrow(sessionId);
        // Persist the user turn incrementally, BEFORE streaming the reply — a crash during
        // the coach's turn still leaves the user's answer in the transcript.
        transcriptWriter.append(sessionId, "user", userMessage);

        Task task = taskService.get(session.getTaskId());
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        streamCoachTurn(emitter, sessionId, buildReplyPrompt(task, sessionId));
        return emitter;
    }

    /**
     * Finish the session: mark it {@code FINISHED} and enqueue an {@code AI_DIALOG}
     * EVALUATION job that grades the persisted transcript. Returns the created job id.
     */
    @Transactional
    public Long finish(Long sessionId) {
        MockSession session = findOrThrow(sessionId);
        Task task = taskService.get(session.getTaskId());

        session.setState(MockSessionState.FINISHED);
        session.setFinishedAt(Instant.now());
        mockSessionRepository.save(session);

        // Grade the persisted transcript out-of-band: AI_DIALOG EVALUATION → award → terminal state.
        EvaluationInput input = new EvaluationInput(task.getId(), task.getTypeKey(), null, null, sessionId);
        Job job = jobService.enqueue(JobType.EVALUATION, input, task.getGoalId(), task.getId());
        return job.getId();
    }

    /** The session envelope, for review after the interview. */
    @Transactional(readOnly = true)
    public MockSession getSession(Long sessionId) {
        return findOrThrow(sessionId);
    }

    /** The persisted transcript in turn order, for review after the interview. */
    @Transactional(readOnly = true)
    public List<MockMessage> transcript(Long sessionId) {
        return mockMessageRepository.findBySessionIdOrderBySeqAsc(sessionId);
    }

    private MockSession findOrThrow(Long sessionId) {
        return mockSessionRepository.findById(sessionId)
                .orElseThrow(() -> new MockSessionNotFoundException(sessionId));
    }

    /** Run the coach's turn: stream tokens through the SSE seam and persist the reply on completion. */
    private void streamCoachTurn(SseEmitter emitter, Long sessionId, String prompt) {
        MockStreamListener listener = new MockStreamListener(emitter, transcriptWriter, sessionId);
        coachChatExecutor.execute(() -> {
            try {
                openRouterClient.stream(prompt, listener);
            } catch (RuntimeException ex) {
                // A synchronous failure from the port is surfaced on the SSE stream too.
                listener.onError(ex);
            }
        });
    }

    private String buildOpeningPrompt(Task task) {
        return contextAssembler.assemble(task.getGoalId())
                + "\n\n## Mock interview\n"
                + "You are the interviewer for this task: " + task.getTitle() + ".\n"
                + "Ask the candidate your first interview question. Reply with the question only.\n"
                + "coach:";
    }

    private String buildReplyPrompt(Task task, Long sessionId) {
        StringBuilder sb = new StringBuilder(contextAssembler.assemble(task.getGoalId()));
        sb.append("\n\n## Mock interview\n");
        sb.append("You are the interviewer for this task: ").append(task.getTitle()).append(".\n");
        sb.append("Continue the interview: probe the candidate's latest answer or move on.\n\n");
        sb.append("## Transcript so far\n");
        for (MockMessage message : mockMessageRepository.findBySessionIdOrderBySeqAsc(sessionId)) {
            sb.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        sb.append("coach:");
        return sb.toString();
    }
}
