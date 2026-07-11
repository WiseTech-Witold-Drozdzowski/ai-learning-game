package com.careercoach.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobRepository;
import com.careercoach.jobs.JobRunner;
import com.careercoach.jobs.JobStatus;
import com.careercoach.tasks.repository.TaskRepository;
import com.careercoach.coach.domain.PlanningOutput;
import com.careercoach.coach.domain.ProposedGoal;
import com.careercoach.coach.domain.ProposedTask;

/**
 * End-to-end PLANNING slice (issue-2) against a real Postgres (provided by
 * {@code run-tests.sh}), with the AI port replaced by a Mockito stub returning
 * deterministic JSON. Drives {@code plan → Job → proposals saved} through REST +
 * the deterministic {@code pollOnce()} of issue-1, for both modes, plus the
 * non-ACTIVE rejection. Red phase: the handler/service throw, so the job FAILs
 * and assertions fail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(PlanningIntegrationTest.SyncExecutorConfig.class)
class PlanningIntegrationTest {

    private static final String DETERMINISTIC_JSON = """
            {"goals":[{"title":"Sub-goal one","description":"decomposed"}],
             "tasks":[{"title":"Task one","description":"generated","typeKey":"HONOR_CHECK","skillKeys":["JAVA"]}]}
            """;

    @TestConfiguration
    static class SyncExecutorConfig {

        @Bean(name = "jobExecutor")
        Executor jobExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobRunner jobRunner;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private TaskRepository taskRepository;

    @MockitoBean
    private OpenRouterClient openRouterClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();
        taskRepository.deleteAll();
        goalRepository.deleteAll();

        when(openRouterClient.complete(anyString()))
                .thenReturn(new OpenRouterCompletion(DETERMINISTIC_JSON));

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private Goal saveGoal(GoalState state) {
        return goalRepository.save(Goal.builder()
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Strategic goal")
                .description("desc")
                .state(state)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .build());
    }

    @Test
    void plan_generateTasks_shouldRunJobToDoneAndSaveTaskProposals() throws Exception {
        // Arrange
        Goal goal = saveGoal(GoalState.ACTIVE);

        // Act — enqueue via REST, then process deterministically
        mockMvc.perform(post("/api/goals/" + goal.getId() + "/plan").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"GENERATE_TASKS\"}"))
                .andExpect(status().isAccepted());
        jobRunner.pollOnce();

        // Assert — job DONE with the proposals persisted in its output
        Job job = jobRepository.findAll().get(0);
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        PlanningOutput output = (PlanningOutput) job.getOutput();
        assertThat(output.proposedTasks()).extracting(ProposedTask::title).containsExactly("Task one");
        assertThat(output.proposedGoals()).isNullOrEmpty();
        // No real tasks are created until proposals are accepted.
        assertThat(taskRepository.findAll()).isEmpty();
    }

    @Test
    void plan_decompose_shouldRunJobToDoneAndPersistProposedGoals() throws Exception {
        // Arrange
        Goal goal = saveGoal(GoalState.PROPOSED);

        // Act
        mockMvc.perform(post("/api/goals/" + goal.getId() + "/plan").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DECOMPOSE\"}"))
                .andExpect(status().isAccepted());
        jobRunner.pollOnce();

        // Assert — job DONE and a PROPOSED child goal was saved under the target goal
        Job job = jobRepository.findAll().get(0);
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);

        List<Goal> children = goalRepository.findAll().stream()
                .filter(g -> goal.getId().equals(g.getParentId()))
                .toList();
        assertThat(children).hasSize(1);
        assertThat(children.get(0).getState()).isEqualTo(GoalState.PROPOSED);
        assertThat(children.get(0).getCreatedBy()).isEqualTo(GoalCreatedBy.COACH);
        assertThat(children.get(0).getTitle()).isEqualTo("Sub-goal one");

        PlanningOutput output = (PlanningOutput) job.getOutput();
        assertThat(output.proposedGoals()).extracting(ProposedGoal::title).containsExactly("Sub-goal one");
    }

    @Test
    void plan_generateTasks_shouldReturnConflict_whenGoalNotActive() throws Exception {
        // Arrange — a non-ACTIVE goal
        Goal goal = saveGoal(GoalState.PROPOSED);

        // Act / Assert — rejected synchronously, no job enqueued
        mockMvc.perform(post("/api/goals/" + goal.getId() + "/plan").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"GENERATE_TASKS\"}"))
                .andExpect(status().isConflict());
        assertThat(jobRepository.findAll()).isEmpty();
    }
}
