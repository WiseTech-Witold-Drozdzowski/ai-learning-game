package com.careercoach.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.exception.TaskNotFoundException;
import com.careercoach.tasks.repository.TaskRepository;

/**
 * Unit tests for {@link TaskService#getGoalId} (issue-7) — repository is mocked.
 * Red phase: the skeleton throws {@code NotImplementedException}.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceGetGoalIdTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskTypeDefinitionService taskTypeDefinitionService;

    @Mock
    private GamificationService gamificationService;

    @Mock
    private AiVerificationLauncher aiVerificationLauncher;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskTypeDefinitionService, gamificationService,
                aiVerificationLauncher);
    }

    @Test
    void shouldReturnGoalId_whenTaskExists() {
        // Arrange
        Task task = Task.builder().id(100L).goalId(42L).build();
        when(taskRepository.findById(100L)).thenReturn(Optional.of(task));

        // Act
        Long goalId = service.getGoalId(100L);

        // Assert
        assertThat(goalId).isEqualTo(42L);
    }

    @Test
    void shouldThrowTaskNotFound_whenTaskMissing() {
        // Arrange
        when(taskRepository.findById(999L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.getGoalId(999L))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
