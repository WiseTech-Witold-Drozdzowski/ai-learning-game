package com.careercoach.goals.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;

/**
 * Unit tests for {@link GoalService#resetAllExpEarned} (issue-7) — repository is mocked.
 * Red phase: the skeleton throws {@code NotImplementedException}.
 */
@ExtendWith(MockitoExtension.class)
class GoalServiceResetExpTest {

    @Mock
    private GoalRepository goalRepository;

    @Captor
    private ArgumentCaptor<Goal> goalCaptor;

    private GoalService service;

    @BeforeEach
    void setUp() {
        service = new GoalService(goalRepository);
    }

    @Test
    void shouldZeroExpEarnedForEveryGoal_whenReset() {
        // Arrange
        Goal goal1 = Goal.builder()
                .id(1L)
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Goal 1")
                .description("desc")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(50L)
                .build();
        Goal goal2 = Goal.builder()
                .id(2L)
                .parentId(1L)
                .kind(GoalKind.LEVEL)
                .title("Goal 2")
                .description("desc")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(30L)
                .build();
        when(goalRepository.findAll()).thenReturn(List.of(goal1, goal2));
        when(goalRepository.save(any(Goal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.resetAllExpEarned();

        // Assert
        verify(goalRepository, times(2)).save(goalCaptor.capture());
        List<Goal> saved = goalCaptor.getAllValues();
        for (Goal g : saved) {
            assertThat(g.getExpEarned()).isEqualTo(0L);
        }
    }

    @Test
    void shouldSaveNothing_whenNoGoalsExist() {
        // Arrange
        when(goalRepository.findAll()).thenReturn(List.of());

        // Act
        service.resetAllExpEarned();

        // Assert
        verify(goalRepository, never()).save(any(Goal.class));
    }
}
