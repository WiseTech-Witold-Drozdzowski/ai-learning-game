package com.careercoach.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.service.GoalService;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobResult;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;

import tools.jackson.databind.json.JsonMapper;

/**
 * Unit test for {@link PlanningJobHandler} (BACKEND_DESIGN §4): maps
 * {@code input → output} against a mocked OpenRouter port returning deterministic
 * JSON. DECOMPOSE persists {@code PROPOSED} sub-goals; GENERATE_TASKS reports task
 * proposals without persisting tasks. Red phase: {@code handle} throws.
 */
class PlanningJobHandlerTest {

    private final ContextAssembler assembler = mock(ContextAssembler.class);
    private final OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
    private final GoalService goalService = mock(GoalService.class);
    private final PlanningJobHandler handler =
            new PlanningJobHandler(assembler, openRouterClient, goalService, JsonMapper.builder().build());

    private static Goal proposedChild(Long id, String title, String description) {
        return Goal.builder()
                .id(id)
                .parentId(7L)
                .kind(GoalKind.LEVEL)
                .title(title)
                .description(description)
                .state(GoalState.PROPOSED)
                .createdBy(GoalCreatedBy.COACH)
                .orderIndex(0)
                .expEarned(0L)
                .build();
    }

    @Test
    void type_and_inputType_shouldIdentifyPlanning() {
        // Assert — contract wiring for the registry
        assertThat(handler.type()).isEqualTo(JobType.PLANNING);
        assertThat(handler.inputType()).isEqualTo(PlanningInput.class);
    }

    @Test
    void handle_shouldPersistProposedGoalsAndReportThem_whenDecompose() {
        // Arrange
        when(assembler.assemble(7L)).thenReturn("PROMPT");
        when(openRouterClient.complete(anyString())).thenReturn(new OpenRouterCompletion(
                "{\"goals\":[{\"title\":\"Sub A\",\"description\":\"da\"},"
                        + "{\"title\":\"Sub B\",\"description\":\"db\"}]}"));
        when(goalService.createProposedChild(eq(7L), anyString(), any()))
                .thenAnswer(inv -> proposedChild(100L, inv.getArgument(1), inv.getArgument(2)));
        Job job = new Job(JobType.PLANNING, JobStatus.RUNNING);

        // Act
        JobResult result = handler.handle(job, new PlanningInput(7L, PlanningMode.DECOMPOSE));

        // Assert
        PlanningOutput output = (PlanningOutput) result.output();
        assertThat(output.proposedGoals()).extracting(ProposedGoal::title).containsExactly("Sub A", "Sub B");
        assertThat(output.proposedGoals()).allSatisfy(g -> assertThat(g.id()).isEqualTo(100L));
        assertThat(output.proposedTasks()).isNullOrEmpty();
        verify(goalService, times(2)).createProposedChild(eq(7L), anyString(), any());
    }

    @Test
    void handle_shouldReportTaskProposalsWithoutPersistingThem_whenGenerateTasks() {
        // Arrange
        when(assembler.assemble(7L)).thenReturn("PROMPT");
        when(openRouterClient.complete(anyString())).thenReturn(new OpenRouterCompletion(
                "{\"tasks\":[{\"title\":\"Do a mock\",\"description\":\"d\","
                        + "\"typeKey\":\"MOCK\",\"skillKeys\":[\"JAVA\",\"COMMUNICATION\"]}]}"));
        Job job = new Job(JobType.PLANNING, JobStatus.RUNNING);

        // Act
        JobResult result = handler.handle(job, new PlanningInput(7L, PlanningMode.GENERATE_TASKS));

        // Assert
        PlanningOutput output = (PlanningOutput) result.output();
        assertThat(output.proposedTasks()).hasSize(1);
        ProposedTask task = output.proposedTasks().get(0);
        assertThat(task.title()).isEqualTo("Do a mock");
        assertThat(task.typeKey()).isEqualTo("MOCK");
        assertThat(task.skillKeys()).containsExactly("JAVA", "COMMUNICATION");
        assertThat(output.proposedGoals()).isNullOrEmpty();
        // Tasks are NOT persisted by the job — no Goal writes happen either.
        verify(goalService, never()).createProposedChild(any(), anyString(), any());
    }
}
