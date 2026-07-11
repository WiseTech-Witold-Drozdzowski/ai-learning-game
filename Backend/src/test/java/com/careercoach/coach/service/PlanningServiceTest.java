package com.careercoach.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.service.GoalService;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobService;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.service.TaskService;
import com.careercoach.coach.domain.PlanningMode;
import com.careercoach.coach.domain.ProposedTask;
import com.careercoach.coach.domain.exception.IllegalPlanningStateException;

/**
 * Unit test for {@link PlanningService} (BACKEND_DESIGN §4): enqueues PLANNING
 * jobs, rejects GENERATE_TASKS unless the goal is ACTIVE, and turns accepted task
 * proposals into real {@code TODO} tasks. Red phase: the service methods throw.
 */
@ExtendWith(MockitoExtension.class)
class PlanningServiceTest {

    @Mock
    private GoalService goalService;

    @Mock
    private TaskService taskService;

    @Mock
    private JobService jobService;

    @InjectMocks
    private PlanningService planningService;

    private static Goal goal(Long id, GoalState state) {
        return Goal.builder()
                .id(id)
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Goal")
                .description("desc")
                .state(state)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .build();
    }

    private static Job jobWithId(Long id) {
        Job job = new Job(JobType.PLANNING, com.careercoach.jobs.JobStatus.QUEUED);
        job.setId(id);
        return job;
    }

    @Test
    void plan_shouldEnqueuePlanningJob_whenDecompose() {
        // Arrange — DECOMPOSE has no ACTIVE requirement
        when(goalService.get(7L)).thenReturn(goal(7L, GoalState.PROPOSED));
        when(jobService.enqueue(eq(JobType.PLANNING), any(PlanningInput.class), eq(7L), isNull()))
                .thenReturn(jobWithId(55L));

        // Act
        Job job = planningService.plan(7L, PlanningMode.DECOMPOSE);

        // Assert
        assertThat(job.getId()).isEqualTo(55L);
        verify(jobService).enqueue(eq(JobType.PLANNING), any(PlanningInput.class), eq(7L), isNull());
    }

    @Test
    void plan_shouldEnqueuePlanningJob_whenGenerateTasksUnderActiveGoal() {
        // Arrange
        when(goalService.get(7L)).thenReturn(goal(7L, GoalState.ACTIVE));
        when(jobService.enqueue(eq(JobType.PLANNING), any(PlanningInput.class), eq(7L), isNull()))
                .thenReturn(jobWithId(56L));

        // Act
        Job job = planningService.plan(7L, PlanningMode.GENERATE_TASKS);

        // Assert
        assertThat(job.getId()).isEqualTo(56L);
        verify(jobService).enqueue(eq(JobType.PLANNING), any(PlanningInput.class), eq(7L), isNull());
    }

    @Test
    void plan_shouldReject_whenGenerateTasksUnderNonActiveGoal() {
        // Arrange
        when(goalService.get(7L)).thenReturn(goal(7L, GoalState.PROPOSED));

        // Act / Assert — the coach only generates tasks under an ACTIVE goal
        assertThatThrownBy(() -> planningService.plan(7L, PlanningMode.GENERATE_TASKS))
                .isInstanceOf(IllegalPlanningStateException.class);
        verifyNoInteractions(jobService);
    }

    @Test
    void acceptTaskProposals_shouldCreateTodoTasks_whenGoalActive() {
        // Arrange
        when(goalService.get(7L)).thenReturn(goal(7L, GoalState.ACTIVE));
        when(taskService.createTodo(eq(7L), anyString(), any(), anyString(), any()))
                .thenAnswer(inv -> Task.builder()
                        .id(1L)
                        .goalId(inv.getArgument(0))
                        .typeKey(inv.getArgument(3))
                        .title(inv.getArgument(1))
                        .description(inv.getArgument(2))
                        .state(TaskState.TODO)
                        .skillKeys(inv.getArgument(4))
                        .expAwarded(0L)
                        .build());
        List<ProposedTask> proposals = List.of(
                new ProposedTask("Task 1", "d1", "MOCK", List.of("JAVA")),
                new ProposedTask("Task 2", "d2", "QUIZ", List.of()));

        // Act
        List<Task> created = planningService.acceptTaskProposals(7L, proposals);

        // Assert
        assertThat(created).hasSize(2);
        assertThat(created).allSatisfy(t -> assertThat(t.getState()).isEqualTo(TaskState.TODO));
        assertThat(created).extracting(Task::getTitle).containsExactly("Task 1", "Task 2");
        verify(taskService, times(2)).createTodo(eq(7L), anyString(), any(), anyString(), any());
    }

    @Test
    void acceptTaskProposals_shouldReject_whenGoalNotActive() {
        // Arrange
        when(goalService.get(7L)).thenReturn(goal(7L, GoalState.PROPOSED));

        // Act / Assert
        assertThatThrownBy(() -> planningService.acceptTaskProposals(7L,
                List.of(new ProposedTask("Task 1", "d1", "MOCK", List.of("JAVA")))))
                .isInstanceOf(IllegalPlanningStateException.class);
        verify(taskService, never()).createTodo(any(), anyString(), any(), anyString(), any());
    }
}
