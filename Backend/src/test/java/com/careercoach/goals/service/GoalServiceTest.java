package com.careercoach.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.domain.exception.GoalInvariantViolationException;
import com.careercoach.goals.domain.exception.GoalNotFoundException;
import com.careercoach.goals.domain.exception.IllegalGoalStateTransitionException;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.goals.web.model.GoalCreateRequest;
import com.careercoach.goals.web.model.GoalNode;

/**
 * Unit tests for {@link GoalService} (issue-3) — repository is mocked. Red
 * phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceTest {

    @Mock
    private GoalRepository goalRepository;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository);
    }

    @Test
    void shouldCreateStrategicGoalInProposedState_whenNoParentId() {
        // Arrange
        lenient().when(goalRepository.countByParentIdIsNull()).thenReturn(0L);
        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        when(goalRepository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());
        GoalCreateRequest req = new GoalCreateRequest("Become senior engineer", "desc", null);

        // Act
        Goal result = service.createStrategic(req);

        // Assert
        assertThat(result.getKind()).isEqualTo(GoalKind.STRATEGIC);
        assertThat(result.getState()).isEqualTo(GoalState.PROPOSED);
        assertThat(result.getCreatedBy()).isEqualTo(GoalCreatedBy.USER);
        assertThat(result.getExpEarned()).isZero();
        assertThat(result.getParentId()).isNull();
        assertThat(result.getTitle()).isEqualTo("Become senior engineer");
    }

    @Test
    void shouldRejectStrategicGoalWithParentId_whenParentIdProvided() {
        // Arrange
        GoalCreateRequest req = new GoalCreateRequest("Bad goal", "desc", 42L);

        // Act / Assert
        assertThatThrownBy(() -> service.createStrategic(req))
                .isInstanceOf(GoalInvariantViolationException.class);
        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    void shouldReturnNestedTree_whenGoalsExist() {
        // Arrange
        Goal root = Goal.builder()
                .id(1L)
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Root")
                .state(GoalState.PROPOSED)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build();
        Goal child = Goal.builder()
                .id(2L)
                .parentId(1L)
                .kind(GoalKind.LEVEL)
                .title("Child")
                .state(GoalState.PROPOSED)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build();
        when(goalRepository.findAllByOrderByOrderIndexAscIdAsc()).thenReturn(List.of(root, child));

        // Act
        List<GoalNode> result = service.getTree();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).children()).hasSize(1);
        assertThat(result.get(0).children().get(0).id()).isEqualTo(2L);
        assertThat(result.get(0).children().get(0).title()).isEqualTo("Child");
    }

    @Test
    void shouldReturnEmptyTree_whenNoGoals() {
        // Arrange
        when(goalRepository.findAllByOrderByOrderIndexAscIdAsc()).thenReturn(List.of());

        // Act
        List<GoalNode> result = service.getTree();

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void shouldTransitionProposedToActive_whenAccepted() {
        // Arrange
        Goal goal = Goal.builder()
                .id(1L)
                .kind(GoalKind.STRATEGIC)
                .title("Goal")
                .state(GoalState.PROPOSED)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build();
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Goal result = service.accept(1L);

        // Assert
        assertThat(result.getState()).isEqualTo(GoalState.ACTIVE);
        verify(goalRepository).save(goal);
    }

    @Test
    void shouldRejectAccept_whenStateNotProposed() {
        // Arrange
        Goal goal = Goal.builder()
                .id(1L)
                .kind(GoalKind.STRATEGIC)
                .title("Goal")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build();
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        // Act / Assert
        assertThatThrownBy(() -> service.accept(1L))
                .isInstanceOf(IllegalGoalStateTransitionException.class);
        assertThat(goal.getState()).isEqualTo(GoalState.ACTIVE);
        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    void shouldThrowGoalNotFound_whenAcceptingMissingId() {
        // Arrange
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.accept(99L))
                .isInstanceOf(GoalNotFoundException.class);
    }

    @Test
    void shouldTransitionActiveToClosed_whenClosed() {
        // Arrange
        Goal goal = Goal.builder()
                .id(1L)
                .kind(GoalKind.STRATEGIC)
                .title("Goal")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build();
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Goal result = service.close(1L);

        // Assert
        assertThat(result.getState()).isEqualTo(GoalState.CLOSED);
        verify(goalRepository).save(goal);
    }

    @Test
    void shouldRejectClose_whenStateProposed() {
        // Arrange
        Goal goal = Goal.builder()
                .id(1L)
                .kind(GoalKind.STRATEGIC)
                .title("Goal")
                .state(GoalState.PROPOSED)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build();
        when(goalRepository.findById(1L)).thenReturn(Optional.of(goal));

        // Act / Assert
        assertThatThrownBy(() -> service.close(1L))
                .isInstanceOf(IllegalGoalStateTransitionException.class);
        assertThat(goal.getState()).isEqualTo(GoalState.PROPOSED);
        verify(goalRepository, never()).save(any(Goal.class));
    }

    @Test
    void shouldThrowGoalNotFound_whenClosingMissingId() {
        // Arrange
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.close(99L))
                .isInstanceOf(GoalNotFoundException.class);
    }
}
