package com.careercoach.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.careercoach.goals.domain.exception.GoalNotFoundException;
import com.careercoach.goals.repository.GoalRepository;

/**
 * Unit tests for {@link GoalService#bubbleExp} (issue-5) — repository is mocked.
 * Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceBubbleExpTest {

    @Mock
    private GoalRepository goalRepository;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository);
    }

    private Goal goal(Long id, Long parentId, long expEarned) {
        return Goal.builder()
                .id(id)
                .parentId(parentId)
                .kind(parentId == null ? GoalKind.STRATEGIC : GoalKind.LEVEL)
                .title("Goal " + id)
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(expEarned)
                .build();
    }

    @Test
    void shouldAddAmountToLeafAndEveryAncestor_whenBubblingUpToRoot() {
        // Arrange
        Goal root = goal(1L, null, 0L);
        Goal child = goal(2L, 1L, 0L);
        Goal leaf = goal(3L, 2L, 0L);
        when(goalRepository.findById(3L)).thenReturn(Optional.of(leaf));
        when(goalRepository.findById(2L)).thenReturn(Optional.of(child));
        when(goalRepository.findById(1L)).thenReturn(Optional.of(root));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.bubbleExp(3L, 10L);

        // Assert
        ArgumentCaptor<Goal> captor = ArgumentCaptor.forClass(Goal.class);
        verify(goalRepository, times(3)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Goal::getId, Goal::getExpEarned)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(1L, 10L),
                        org.assertj.core.groups.Tuple.tuple(2L, 10L),
                        org.assertj.core.groups.Tuple.tuple(3L, 10L));
    }

    @Test
    void shouldIncrementOnlyThatGoal_whenBubblingFromRoot() {
        // Arrange
        Goal root = goal(1L, null, 5L);
        when(goalRepository.findById(1L)).thenReturn(Optional.of(root));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.bubbleExp(1L, 7L);

        // Assert
        verify(goalRepository, times(1)).save(any(Goal.class));
        assertThat(root.getExpEarned()).isEqualTo(12L);
    }

    @Test
    void shouldThrowGoalNotFound_whenGoalIdMissing() {
        // Arrange
        when(goalRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.bubbleExp(99L, 10L))
                .isInstanceOf(GoalNotFoundException.class);
    }

    @Test
    void shouldLeaveExpEarnedUnchanged_whenAmountIsZero() {
        // Arrange
        Goal root = goal(1L, null, 5L);
        Goal leaf = goal(2L, 1L, 3L);
        lenient().when(goalRepository.findById(2L)).thenReturn(Optional.of(leaf));
        lenient().when(goalRepository.findById(1L)).thenReturn(Optional.of(root));
        lenient().when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.bubbleExp(2L, 0L);

        // Assert
        assertThat(leaf.getExpEarned()).isEqualTo(3L);
        assertThat(root.getExpEarned()).isEqualTo(5L);
    }
}
