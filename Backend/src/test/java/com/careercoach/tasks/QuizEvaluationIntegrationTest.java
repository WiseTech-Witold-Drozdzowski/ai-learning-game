package com.careercoach.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.coach.EvaluationInput;
import com.careercoach.coach.QuizGenerationService;
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
import com.careercoach.jobs.ExpGainEvent;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobRepository;
import com.careercoach.jobs.JobRunner;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;
import com.careercoach.jobs.SseHub;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.repository.TaskRepository;
import com.careercoach.tasks.service.TaskService;

/**
 * End-to-end AUTO_QUIZ slice (issue-5) against a real Postgres (provided by
 * {@code run-tests.sh}). The AI port is stubbed to <em>generate</em> a quiz with an
 * answer key; grading is then deterministic. Drives {@code generateQuiz → submit
 * (answers) → EVALUATION job → deterministic grade → award → exp accrued} through the
 * real {@link TaskService} and {@code pollOnce()}. Covers score scaling, the
 * passed/failed terminal states, idempotent replay and the {@code exp-gain} SSE event.
 * Red phase: the grader/generator throw, so the job FAILs and assertions fail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(QuizEvaluationIntegrationTest.SyncExecutorConfig.class)
class QuizEvaluationIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean(name = "jobExecutor")
        Executor jobExecutor() {
            return Runnable::run;
        }
    }

    /** A two-question quiz with answer key ["A", "B"] returned by the (mocked) generator. */
    private static final String GENERATED_QUIZ_JSON = """
            {"questions":[
              {"prompt":"Q1","options":["A","B"],"answer":"A"},
              {"prompt":"Q2","options":["A","B"],"answer":"B"}
            ]}""";

    @Autowired private TaskService taskService;
    @Autowired private QuizGenerationService quizGenerationService;
    @Autowired private JobRunner jobRunner;
    @Autowired private JobRepository jobRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private ExpEventRepository expEventRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private SkillDefinitionRepository skillDefinitionRepository;
    @Autowired private CareerProfileRepository careerProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskTypeDefinitionService taskTypeDefinitionService;

    @MockitoBean private OpenRouterClient openRouterClient;
    @MockitoSpyBean private SseHub sseHub;

    private User user;
    private Goal goal;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        expEventRepository.deleteAll();
        skillRepository.deleteAll();
        taskRepository.deleteAll();
        goalRepository.deleteAll();
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        if (!skillDefinitionRepository.existsById("JAVA")) {
            skillDefinitionRepository.save(
                    SkillDefinition.builder().key("JAVA").displayName("Java").category("language").build());
        }
        // AUTO_QUIZ, expBase 50, exp scaled by score.
        taskTypeDefinitionService.upsert("QUIZ",
                new TaskTypeUpsertRequest("Auto quiz", VerificationMethod.AUTO_QUIZ, 50, true, false));

        user = userRepository.save(new User("grad@example.com", "sub-grad", "Grad"));
        careerProfileRepository.save(new CareerProfile(user.getId(), 0L, 1, AvatarState.initial()));

        goal = goalRepository.save(Goal.builder()
                .parentId(null).kind(GoalKind.STRATEGIC).title("Goal").description("desc")
                .state(GoalState.ACTIVE).createdBy(GoalCreatedBy.USER).orderIndex(0).expEarned(0L)
                .build());
    }

    private Task saveInProgressQuizTask() {
        return taskRepository.save(Task.builder()
                .goalId(goal.getId()).typeKey("QUIZ").title("Java quiz").description("desc")
                .state(TaskState.IN_PROGRESS).skillKeys(List.of("JAVA")).expAwarded(0L)
                .build());
    }

    private void stubQuizGeneration() {
        when(openRouterClient.complete(anyString())).thenReturn(new OpenRouterCompletion(GENERATED_QUIZ_JSON));
    }

    @Test
    void generateQuiz_shouldPersistQuizWithAnswerKey() {
        // Arrange
        stubQuizGeneration();
        Task task = saveInProgressQuizTask();

        // Act
        quizGenerationService.generate(task.getId());

        // Assert — the quiz + answer key is persisted as part of the task state
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getQuiz()).isNotNull();
        assertThat(reloaded.getQuiz().questions()).hasSize(2);
        assertThat(reloaded.getQuiz().questions().get(0).answer()).isEqualTo("A");
        assertThat(reloaded.getQuiz().questions().get(1).answer()).isEqualTo("B");
    }

    @Test
    void submit_shouldCreateEvaluationJobWithAnswers_andSetInProgress() {
        // Arrange
        stubQuizGeneration();
        Task task = saveInProgressQuizTask();
        quizGenerationService.generate(task.getId());

        // Act
        Task submitted = taskService.submit(task.getId(), user.getId(), null, List.of("A", "B"));

        // Assert — an EVALUATION job carrying the answers is created; nothing awarded yet
        assertThat(submitted.getState()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(submitted.getVerificationJobId()).isNotNull();

        List<Job> jobs = jobRepository.findAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getType()).isEqualTo(JobType.EVALUATION);
        assertThat(jobs.get(0).getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(jobs.get(0).getInput()).isInstanceOfSatisfying(EvaluationInput.class,
                in -> assertThat(in.answers()).containsExactly("A", "B"));
        assertThat(expEventRepository.findBySourceTaskId(task.getId())).isEmpty();
    }

    @Test
    void quizJob_shouldAwardFullScaledExpAndCompleteTask_whenAllCorrect() {
        // Arrange — 2/2 correct → 100% → full expBase (50), and an exp-gain SSE event
        stubQuizGeneration();
        Task task = saveInProgressQuizTask();
        quizGenerationService.generate(task.getId());
        taskService.submit(task.getId(), user.getId(), null, List.of("A", "B"));

        // Act
        jobRunner.pollOnce();

        // Assert — job DONE, task DONE, exp = expBase, counters bubbled, SSE emitted
        assertThat(jobRepository.findAll().get(0).getStatus()).isEqualTo(JobStatus.DONE);
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TaskState.DONE);
        assertThat(reloaded.getExpAwarded()).isEqualTo(50L);
        assertThat(careerProfileRepository.findById(user.getId()).orElseThrow().getTotalExp()).isEqualTo(50L);
        assertThat(goalRepository.findById(goal.getId()).orElseThrow().getExpEarned()).isEqualTo(50L);
        verify(sseHub, atLeastOnce()).emit(any(ExpGainEvent.class));
    }

    @Test
    void quizJob_shouldRejectTaskWithoutExp_whenAllWrong() {
        // Arrange — 0/2 correct → 0% → failed
        stubQuizGeneration();
        Task task = saveInProgressQuizTask();
        quizGenerationService.generate(task.getId());
        taskService.submit(task.getId(), user.getId(), null, List.of("B", "A"));

        // Act
        jobRunner.pollOnce();

        // Assert — task REJECTED, no exp accrued
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TaskState.REJECTED);
        assertThat(reloaded.getExpAwarded()).isZero();
        assertThat(careerProfileRepository.findById(user.getId()).orElseThrow().getTotalExp()).isZero();
    }

    @Test
    void quizJob_replayed_shouldNotDoubleExp() {
        // Arrange — a passing quiz graded once
        stubQuizGeneration();
        Task task = saveInProgressQuizTask();
        quizGenerationService.generate(task.getId());
        taskService.submit(task.getId(), user.getId(), null, List.of("A", "B"));
        jobRunner.pollOnce();

        // Act — re-run the SAME job (same jobId → same idempotency attempt key)
        Job job = jobRepository.findAll().get(0);
        job.setStatus(JobStatus.QUEUED);
        job.setLockedAt(null);
        job.setNextRunAt(null);
        jobRepository.save(job);
        jobRunner.pollOnce();

        // Assert — exp accrued exactly once
        assertThat(expEventRepository.findBySourceTaskId(task.getId())).hasSize(1);
        assertThat(careerProfileRepository.findById(user.getId()).orElseThrow().getTotalExp()).isEqualTo(50L);
        assertThat(goalRepository.findById(goal.getId()).orElseThrow().getExpEarned()).isEqualTo(50L);
    }
}
