package com.careercoach.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
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

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
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
import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobRepository;
import com.careercoach.jobs.JobRunner;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.repository.TaskRepository;
import com.careercoach.tasks.service.TaskService;

/**
 * End-to-end EVALUATION slice (issue-4) against a real Postgres (provided by
 * {@code run-tests.sh}) with the AI port replaced by a Mockito stub returning a
 * deterministic grade. Drives {@code submit (AI_ARTIFACT_REVIEW) → EVALUATION job →
 * award → exp accrued} through the real {@link TaskService} and the deterministic
 * {@code pollOnce()}. Covers exp clamping, the passed/failed terminal states and
 * idempotent replay. Red phase: the handler/launcher/service throw, so the job
 * FAILs and assertions fail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(EvaluationJobIntegrationTest.SyncExecutorConfig.class)
class EvaluationJobIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean(name = "jobExecutor")
        Executor jobExecutor() {
            return Runnable::run;
        }
    }

    @Autowired private TaskService taskService;
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
        taskTypeDefinitionService.upsert("AI_REVIEW",
                new TaskTypeUpsertRequest("AI artifact review", VerificationMethod.AI_ARTIFACT_REVIEW, 50, false, false));

        user = userRepository.save(new User("grad@example.com", "sub-grad", "Grad"));
        careerProfileRepository.save(new CareerProfile(user.getId(), 0L, 1, AvatarState.initial()));

        goal = goalRepository.save(Goal.builder()
                .parentId(null).kind(GoalKind.STRATEGIC).title("Goal").description("desc")
                .state(GoalState.ACTIVE).createdBy(GoalCreatedBy.USER).orderIndex(0).expEarned(0L)
                .build());
    }

    private Task saveInProgressTask() {
        return taskRepository.save(Task.builder()
                .goalId(goal.getId()).typeKey("AI_REVIEW").title("Build a REST API").description("desc")
                .state(TaskState.IN_PROGRESS).skillKeys(List.of("JAVA")).expAwarded(0L)
                .build());
    }

    private void stubGrade(String json) {
        when(openRouterClient.complete(anyString())).thenReturn(new OpenRouterCompletion(json));
    }

    @Test
    void submit_shouldCreateEvaluationJobAndSetInProgress() {
        // Arrange
        Task task = saveInProgressTask();

        // Act
        Task submitted = taskService.submit(task.getId(), user.getId(), "my artifact");

        // Assert — routed to an EVALUATION job; task waits IN_PROGRESS; nothing awarded yet
        assertThat(submitted.getState()).isEqualTo(TaskState.IN_PROGRESS);
        assertThat(submitted.getVerificationJobId()).isNotNull();

        List<Job> jobs = jobRepository.findAll();
        assertThat(jobs).hasSize(1);
        assertThat(jobs.get(0).getType()).isEqualTo(JobType.EVALUATION);
        assertThat(jobs.get(0).getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(expEventRepository.findBySourceTaskId(task.getId())).isEmpty();
    }

    @Test
    void evaluationJob_shouldAwardClampedExpAndCompleteTask_whenPassed() {
        // Arrange — the AI proposes 1000 exp, far above the type's expBase of 50
        stubGrade("{\"score\":95,\"passed\":true,\"feedback\":\"Great\","
                + "\"skills\":[{\"skillKey\":\"JAVA\",\"exp\":1000}]}");
        Task task = saveInProgressTask();
        taskService.submit(task.getId(), user.getId(), "my artifact");

        // Act
        jobRunner.pollOnce();

        // Assert — job DONE, exp clamped to expBase, task DONE, counters bubbled
        Job job = jobRepository.findAll().get(0);
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TaskState.DONE);
        assertThat(reloaded.getExpAwarded()).isEqualTo(50L);

        assertThat(expEventRepository.findBySourceTaskId(task.getId())).hasSize(1);
        assertThat(careerProfileRepository.findById(user.getId()).orElseThrow().getTotalExp()).isEqualTo(50L);
        assertThat(goalRepository.findById(goal.getId()).orElseThrow().getExpEarned()).isEqualTo(50L);
    }

    @Test
    void evaluationJob_shouldRejectTaskWithoutExp_whenFailed() {
        // Arrange
        stubGrade("{\"score\":20,\"passed\":false,\"feedback\":\"Needs work\","
                + "\"skills\":[{\"skillKey\":\"JAVA\",\"exp\":0}]}");
        Task task = saveInProgressTask();
        taskService.submit(task.getId(), user.getId(), "weak artifact");

        // Act
        jobRunner.pollOnce();

        // Assert — task REJECTED, no exp accrued
        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(TaskState.REJECTED);
        assertThat(reloaded.getExpAwarded()).isZero();
        assertThat(careerProfileRepository.findById(user.getId()).orElseThrow().getTotalExp()).isZero();
    }

    @Test
    void evaluationJob_replayed_shouldNotDoubleExp() {
        // Arrange — a passing grade of 30 (within the cap)
        stubGrade("{\"score\":80,\"passed\":true,\"feedback\":\"Good\","
                + "\"skills\":[{\"skillKey\":\"JAVA\",\"exp\":30}]}");
        Task task = saveInProgressTask();
        taskService.submit(task.getId(), user.getId(), "my artifact");
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
        assertThat(careerProfileRepository.findById(user.getId()).orElseThrow().getTotalExp()).isEqualTo(30L);
        assertThat(goalRepository.findById(goal.getId()).orElseThrow().getExpEarned()).isEqualTo(30L);
    }
}
