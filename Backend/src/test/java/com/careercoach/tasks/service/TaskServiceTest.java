package com.careercoach.tasks.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.gamification.service.AwardCommand;
import com.careercoach.gamification.service.AwardResult;
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.domain.exception.ArtifactRequiredException;
import com.careercoach.tasks.domain.exception.IllegalTaskStateTransitionException;
import com.careercoach.tasks.domain.exception.TaskNotFoundException;
import com.careercoach.tasks.domain.exception.UnsupportedVerificationMethodException;
import com.careercoach.tasks.repository.TaskRepository;

/**
 * Unit tests for {@link TaskService} (issue-6) — all collaborators are mocked.
 * Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskTypeDefinitionService taskTypeDefinitionService;

    @Mock
    private GamificationService gamificationService;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskTypeDefinitionService, gamificationService);
    }

    private static Task task(TaskState state, String typeKey, List<String> skillKeys) {
        return Task.builder()
                .id(1L)
                .goalId(5L)
                .typeKey(typeKey)
                .title("Do the thing")
                .description("desc")
                .state(state)
                .skillKeys(skillKeys)
                .artifact(null)
                .expAwarded(0L)
                .verificationJobId(null)
                .build();
    }

    private static TaskTypeDefinition typeDefinition(
            VerificationMethod method, int expBase, boolean requiresArtifact) {
        return TaskTypeDefinition.builder()
                .key("HONOR_CHECK")
                .displayName("Honor check")
                .verificationMethod(method)
                .expBase(expBase)
                .expScaleByScore(false)
                .requiresArtifact(requiresArtifact)
                .build();
    }

    // --- get ---

    @Test
    void get_shouldReturnTask_whenExists() {
        // Arrange
        Task existing = task(TaskState.TODO, "HONOR_CHECK", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        Task result = service.get(1L);

        // Assert
        assertThat(result).isSameAs(existing);
    }

    @Test
    void get_shouldThrowTaskNotFound_whenMissing() {
        // Arrange
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(TaskNotFoundException.class);
    }

    // --- start ---

    @Test
    void start_shouldTransitionTodoToInProgress_whenTaskIsTodo() {
        // Arrange
        Task existing = task(TaskState.TODO, "HONOR_CHECK", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Task result = service.start(1L);

        // Assert
        assertThat(result.getState()).isEqualTo(TaskState.IN_PROGRESS);
    }

    @ParameterizedTest
    @EnumSource(value = TaskState.class, names = {"IN_PROGRESS", "DONE", "REJECTED"})
    void start_shouldThrowIllegalTaskStateTransition_whenStateNotTodo(TaskState state) {
        // Arrange
        Task existing = task(state, "HONOR_CHECK", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

        // Act / Assert
        assertThatThrownBy(() -> service.start(1L)).isInstanceOf(IllegalTaskStateTransitionException.class);
        verify(taskRepository, never()).save(any(Task.class));
    }

    @Test
    void start_shouldThrowTaskNotFound_whenMissing() {
        // Arrange
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.start(99L)).isInstanceOf(TaskNotFoundException.class);
    }

    // --- submit: not found ---

    @Test
    void submit_shouldThrowTaskNotFound_whenMissing() {
        // Arrange
        when(taskRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.submit(99L, 1L, null)).isInstanceOf(TaskNotFoundException.class);
    }

    // --- submit: HONOR ---

    @Test
    void submit_shouldAwardAndCompleteTask_whenHonor() {
        // Arrange
        Task existing = task(TaskState.IN_PROGRESS, "HONOR_CHECK", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskTypeDefinitionService.get("HONOR_CHECK"))
                .thenReturn(typeDefinition(VerificationMethod.HONOR, 10, false));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<AwardCommand> cmdCaptor = ArgumentCaptor.forClass(AwardCommand.class);
        when(gamificationService.award(cmdCaptor.capture()))
                .thenReturn(AwardResult.builder().applied(true).totalGranted(10L).build());

        // Act
        Task result = service.submit(1L, 42L, null);

        // Assert
        AwardCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.userId()).isEqualTo(42L);
        assertThat(cmd.sourceTaskId()).isEqualTo(1L);
        assertThat(cmd.attemptId()).isNull();
        assertThat(cmd.typeKey()).isEqualTo("HONOR_CHECK");
        assertThat(cmd.goalId()).isEqualTo(5L);
        assertThat(cmd.skillAwards()).hasSize(1);
        assertThat(cmd.skillAwards().get(0).skillKey()).isEqualTo("JAVA");
        assertThat(cmd.skillAwards().get(0).expProposed()).isEqualTo(10);

        assertThat(result.getState()).isEqualTo(TaskState.DONE);
        assertThat(result.getExpAwarded()).isEqualTo(10L);
        assertThat(result.getVerificationJobId()).isNull();
    }

    // --- submit: HONOR_WITH_PROOF ---

    @Test
    void submit_shouldStoreArtifactAndAward_whenHonorWithProofAndProofProvided() {
        // Arrange
        Task existing = task(TaskState.IN_PROGRESS, "HONOR_PROOF", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskTypeDefinitionService.get("HONOR_PROOF"))
                .thenReturn(typeDefinition(VerificationMethod.HONOR_WITH_PROOF, 20, false));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gamificationService.award(any(AwardCommand.class)))
                .thenReturn(AwardResult.builder().applied(true).totalGranted(20L).build());

        // Act
        Task result = service.submit(1L, 42L, "http://proof.example/screenshot.png");

        // Assert
        assertThat(result.getArtifact()).isEqualTo("http://proof.example/screenshot.png");
        assertThat(result.getState()).isEqualTo(TaskState.DONE);
        assertThat(result.getExpAwarded()).isEqualTo(20L);
    }

    @Test
    void submit_shouldThrowArtifactRequired_whenRequiresArtifactAndProofMissing() {
        // Arrange
        Task existing = task(TaskState.IN_PROGRESS, "HONOR_PROOF", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskTypeDefinitionService.get("HONOR_PROOF"))
                .thenReturn(typeDefinition(VerificationMethod.HONOR_WITH_PROOF, 20, true));

        // Act / Assert
        assertThatThrownBy(() -> service.submit(1L, 42L, null)).isInstanceOf(ArtifactRequiredException.class);
        verifyNoInteractions(gamificationService);
        assertThat(existing.getState()).isEqualTo(TaskState.IN_PROGRESS);
    }

    @Test
    void submit_shouldThrowArtifactRequired_whenRequiresArtifactAndProofBlank() {
        // Arrange
        Task existing = task(TaskState.IN_PROGRESS, "HONOR_PROOF", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskTypeDefinitionService.get("HONOR_PROOF"))
                .thenReturn(typeDefinition(VerificationMethod.HONOR_WITH_PROOF, 20, true));

        // Act / Assert
        assertThatThrownBy(() -> service.submit(1L, 42L, "   ")).isInstanceOf(ArtifactRequiredException.class);
        verifyNoInteractions(gamificationService);
        assertThat(existing.getState()).isEqualTo(TaskState.IN_PROGRESS);
    }

    @Test
    void submit_shouldSucceed_whenRequiresArtifactAndProofProvided() {
        // Arrange
        Task existing = task(TaskState.IN_PROGRESS, "HONOR_PROOF", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskTypeDefinitionService.get("HONOR_PROOF"))
                .thenReturn(typeDefinition(VerificationMethod.HONOR_WITH_PROOF, 20, true));
        when(taskRepository.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(gamificationService.award(any(AwardCommand.class)))
                .thenReturn(AwardResult.builder().applied(true).totalGranted(20L).build());

        // Act
        Task result = service.submit(1L, 42L, "http://proof.example");

        // Assert
        assertThat(result.getArtifact()).isEqualTo("http://proof.example");
        assertThat(result.getState()).isEqualTo(TaskState.DONE);
    }

    // --- submit: unsupported ---

    @ParameterizedTest
    @EnumSource(value = VerificationMethod.class, names = {"AUTO_QUIZ", "AI_DIALOG", "AI_ARTIFACT_REVIEW"})
    void submit_shouldThrowUnsupportedVerificationMethod_whenNotHonorBased(VerificationMethod method) {
        // Arrange
        Task existing = task(TaskState.IN_PROGRESS, "OTHER_TYPE", List.of("JAVA"));
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(taskTypeDefinitionService.get("OTHER_TYPE")).thenReturn(typeDefinition(method, 10, false));

        // Act / Assert
        assertThatThrownBy(() -> service.submit(1L, 42L, null))
                .isInstanceOf(UnsupportedVerificationMethodException.class);
        verifyNoInteractions(gamificationService);
        assertThat(existing.getState()).isEqualTo(TaskState.IN_PROGRESS);
    }

    // --- submit: idempotency ---

    @Test
    void submit_shouldReturnUnchanged_whenTaskAlreadyDone() {
        // Arrange
        Task existing = task(TaskState.DONE, "HONOR_CHECK", List.of("JAVA"));
        existing.setExpAwarded(10L);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(existing));

        // Act
        Task result = service.submit(1L, 42L, null);

        // Assert
        assertThat(result.getState()).isEqualTo(TaskState.DONE);
        assertThat(result.getExpAwarded()).isEqualTo(10L);
        verifyNoInteractions(gamificationService);
        verify(taskRepository, never()).save(any(Task.class));
    }
}
