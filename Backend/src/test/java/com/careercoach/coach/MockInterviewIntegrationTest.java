package com.careercoach.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.ai.OpenRouterStreamListener;
import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.coach.service.ContextAssembler;
import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.config.web.TaskTypeUpsertRequest;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobRepository;
import com.careercoach.jobs.JobRunner;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.repository.TaskRepository;
import com.careercoach.coach.domain.MockMessage;
import com.careercoach.coach.domain.MockSession;
import com.careercoach.coach.domain.MockSessionState;
import com.careercoach.coach.repository.MockMessageRepository;
import com.careercoach.coach.repository.MockSessionRepository;

/**
 * End-to-end mock-interview slice (issue-6) against a real Postgres (provided by
 * {@code run-tests.sh}) with the AI port replaced by a Mockito stub. Drives
 * {@code start → incremental MockMessage persistence → finish → EVALUATION (AI_DIALOG)
 * → award → Task DONE} through the real controllers/service and the deterministic
 * {@code pollOnce()}. Also verifies crash-resilience (a mid-turn failure keeps prior
 * messages), transcript readability, and that the transcript never enters the
 * {@link ContextAssembler}. A synchronous executor makes streaming deterministic.
 * Red phase: the mock service throws, so no session is created.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(MockInterviewIntegrationTest.SyncExecutorConfig.class)
class MockInterviewIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean(name = "coachChatExecutor")
        Executor coachChatExecutor() {
            return Runnable::run;
        }

        @Bean(name = "jobExecutor")
        Executor jobExecutor() {
            return Runnable::run;
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private JobRunner jobRunner;
    @Autowired private JobRepository jobRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private MockSessionRepository mockSessionRepository;
    @Autowired private MockMessageRepository mockMessageRepository;
    @Autowired private ExpEventRepository expEventRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private SkillDefinitionRepository skillDefinitionRepository;
    @Autowired private CareerProfileRepository careerProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskTypeDefinitionService taskTypeDefinitionService;
    @Autowired private ContextAssembler contextAssembler;

    @MockitoBean private OpenRouterClient openRouterClient;

    private MockMvc mockMvc;
    private User user;
    private Goal goal;
    private Task task;

    @BeforeEach
    void setUp() {
        mockMessageRepository.deleteAll();
        mockSessionRepository.deleteAll();
        jobRepository.deleteAll();
        expEventRepository.deleteAll();
        skillRepository.deleteAll();
        taskRepository.deleteAll();
        goalRepository.deleteAll();
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        if (!skillDefinitionRepository.existsById("COMMUNICATION")) {
            skillDefinitionRepository.save(SkillDefinition.builder()
                    .key("COMMUNICATION").displayName("Communication").category("SOFT").build());
        }
        taskTypeDefinitionService.upsert("MOCK",
                new TaskTypeUpsertRequest("Mock interview", VerificationMethod.AI_DIALOG, 60, false, false));

        user = userRepository.save(new User("grad@example.com", "sub-grad", "Grad"));
        careerProfileRepository.save(new CareerProfile(user.getId(), 0L, 1, AvatarState.initial()));
        goal = goalRepository.save(Goal.builder()
                .parentId(null).kind(GoalKind.STRATEGIC).title("Behavioural preparation").description("desc")
                .state(GoalState.ACTIVE).createdBy(GoalCreatedBy.USER).orderIndex(0).expEarned(0L)
                .build());
        task = taskRepository.save(Task.builder()
                .goalId(goal.getId()).typeKey("MOCK").title("Behavioural mock interview").description("desc")
                .state(TaskState.IN_PROGRESS).skillKeys(List.of("COMMUNICATION")).expAwarded(0L)
                .build());

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /** Stub the streaming coach turn: emit {@code fragments} in order, then complete. */
    private void stubStream(String... fragments) {
        doAnswer(inv -> {
            OpenRouterStreamListener listener = inv.getArgument(1);
            for (String fragment : fragments) {
                listener.onToken(fragment);
            }
            listener.onComplete();
            return null;
        }).when(openRouterClient).stream(anyString(), any());
    }

    private void stubGrade(String json) {
        when(openRouterClient.complete(anyString())).thenReturn(new OpenRouterCompletion(json));
    }

    /** POST start and drive the async SSE to completion; returns the SSE body. */
    private String start() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks/" + task.getId() + "/mock/start").with(oauth2Login()))
                .andReturn();
        return mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** POST one user turn and drive the async SSE to completion; returns the SSE body. */
    private String respond(Long sessionId, String message) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mock/" + sessionId + "/messages").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andReturn();
        return mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private Long onlySessionId() {
        List<MockSession> sessions = mockSessionRepository.findAll();
        assertThat(sessions).hasSize(1);
        return sessions.get(0).getId();
    }

    @Test
    void start_shouldCreateActiveSessionAndStreamOpeningQuestionAsSse() throws Exception {
        // Arrange — the coach opens with a two-fragment question
        stubStream("Tell me ", "about a hard bug.");

        // Act
        String body = start();

        // Assert — the client learns the session id, then receives the streamed opening question
        assertThat(body).contains("event:session");
        assertThat(body).contains("event:token");
        assertThat(body.indexOf("Tell me ")).isLessThan(body.indexOf("about a hard bug."));

        // Assert — an ACTIVE session for the task, opening question persisted as the first coach turn
        MockSession session = mockSessionRepository.findAll().get(0);
        assertThat(session.getTaskId()).isEqualTo(task.getId());
        assertThat(session.getState()).isEqualTo(MockSessionState.ACTIVE);

        List<MockMessage> transcript = mockMessageRepository.findBySessionIdOrderBySeqAsc(session.getId());
        assertThat(transcript).singleElement().satisfies(m -> {
            assertThat(m.getRole()).isEqualTo("coach");
            assertThat(m.getContent()).isEqualTo("Tell me about a hard bug.");
            assertThat(m.getSeq()).isZero();
        });
    }

    @Test
    void respond_shouldPersistUserThenCoachWithIncrementalSeq() throws Exception {
        // Arrange — an open session with the coach's opening question (seq 0)
        stubStream("Opening question.");
        start();
        Long sessionId = onlySessionId();

        // Act — the user answers; the coach replies
        stubStream("Good, ", "and then?");
        String body = respond(sessionId, "I fixed a deadlock.");

        // Assert — the coach reply streamed token-by-token
        assertThat(body).contains("event:token").contains("Good, ").contains("and then?");

        // Assert — user turn (seq 1) then coach turn (seq 2) appended incrementally, in order
        List<MockMessage> transcript = mockMessageRepository.findBySessionIdOrderBySeqAsc(sessionId);
        assertThat(transcript).hasSize(3);
        assertThat(transcript.get(1).getRole()).isEqualTo("user");
        assertThat(transcript.get(1).getContent()).isEqualTo("I fixed a deadlock.");
        assertThat(transcript.get(1).getSeq()).isEqualTo(1);
        assertThat(transcript.get(2).getRole()).isEqualTo("coach");
        assertThat(transcript.get(2).getContent()).isEqualTo("Good, and then?");
        assertThat(transcript.get(2).getSeq()).isEqualTo(2);
    }

    @Test
    void respond_shouldKeepPriorMessages_whenTurnIsInterruptedMidStream() throws Exception {
        // Arrange — a session with an opening question and one full exchange (seq 0,1,2)
        stubStream("Opening question.");
        start();
        Long sessionId = onlySessionId();
        stubStream("First reply.");
        respond(sessionId, "First answer.");

        // Act — a turn whose coach stream fails mid-way (the user turn was already saved)
        doAnswer(inv -> {
            OpenRouterStreamListener listener = inv.getArgument(1);
            listener.onToken("Half");
            listener.onError(new RuntimeException("upstream exploded"));
            return null;
        }).when(openRouterClient).stream(anyString(), any());
        String body = respond(sessionId, "Second answer.");

        // Assert — the failure is surfaced on the stream, not silently truncated
        assertThat(body).contains("event:error");

        // Assert — every already-exchanged message survives; the user turn persisted; no coach turn for it
        List<MockMessage> transcript = mockMessageRepository.findBySessionIdOrderBySeqAsc(sessionId);
        assertThat(transcript).hasSize(4);
        assertThat(transcript).extracting(MockMessage::getContent)
                .containsExactly("Opening question.", "First answer.", "First reply.", "Second answer.");
        assertThat(transcript).noneSatisfy(m ->
                assertThat(m.getContent()).isEqualTo("Half"));
    }

    @Test
    void finish_shouldEnqueueAiDialogEvaluationThatGradesTranscriptAndCompletesTask() throws Exception {
        // Arrange — a completed exchange to grade
        stubStream("Opening question.");
        start();
        Long sessionId = onlySessionId();
        stubStream("Coach follow-up.");
        respond(sessionId, "My detailed answer.");
        // The AI proposes 500 exp, far above the type's expBase of 60
        stubGrade("{\"score\":88,\"passed\":true,\"feedback\":\"Strong\","
                + "\"skills\":[{\"skillKey\":\"COMMUNICATION\",\"exp\":500}]}");

        // Act — finish enqueues the EVALUATION job, then the runner grades it
        MvcResult finishResult = mockMvc.perform(
                        post("/api/mock/" + sessionId + "/finish").with(oauth2Login()))
                .andExpect(status().isOk())
                .andReturn();
        String finishBody = finishResult.getResponse().getContentAsString();

        // Assert — an AI_DIALOG EVALUATION job was queued
        List<Job> jobs = jobRepository.findAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getType()).isEqualTo(JobType.EVALUATION);
        assertThat(jobs.get(0).getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(finishBody).contains(String.valueOf(jobs.get(0).getId()));

        // Session is closed for further turns
        assertThat(mockSessionRepository.findById(sessionId).orElseThrow().getState())
                .isEqualTo(MockSessionState.FINISHED);

        // Act — grade
        jobRunner.pollOnce();

        // Assert — task DONE, exp clamped to expBase, counters bubbled, session scored
        assertThat(jobRepository.findAll().get(0).getStatus()).isEqualTo(JobStatus.DONE);
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TaskState.DONE);
        assertThat(reloaded.getExpAwarded()).isEqualTo(60L);
        assertThat(expEventRepository.findBySourceTaskId(task.getId())).hasSize(1);
        assertThat(careerProfileRepository.findById(user.getId()).orElseThrow().getTotalExp()).isEqualTo(60L);
        assertThat(mockSessionRepository.findById(sessionId).orElseThrow().getScore()).isEqualTo(88);
    }

    @Test
    void transcript_shouldBeReadableAfterSession() throws Exception {
        // Arrange
        stubStream("Opening question.");
        start();
        Long sessionId = onlySessionId();
        stubStream("Coach follow-up.");
        respond(sessionId, "My answer.");

        // Act + Assert — the stored transcript is readable for review, in order
        mockMvc.perform(get("/api/mock/" + sessionId).with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.messages.length()").value(3))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.messages[0].content").value("Opening question."))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.messages[1].content").value("My answer."))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.messages[2].content").value("Coach follow-up."));
    }

    @Test
    void contextAssembler_shouldNotContainMockTranscript() throws Exception {
        // Arrange — a session with a distinctive transcript persisted
        stubStream("SECRET_TRANSCRIPT_MARKER opening.");
        start();
        Long sessionId = onlySessionId();
        stubStream("SECRET_TRANSCRIPT_MARKER reply.");
        respond(sessionId, "SECRET_TRANSCRIPT_MARKER answer.");

        // Act — assemble the coach context for the goal
        String prompt = contextAssembler.assemble(goal.getId());

        // Assert — the transcript is deliberately excluded from long-term coach memory
        assertThat(prompt).doesNotContain("SECRET_TRANSCRIPT_MARKER");
    }
}
