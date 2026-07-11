package com.careercoach.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.domain.ExpEvent;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.goals.service.GoalService;
import com.careercoach.jobs.ExpGainEvent;
import com.careercoach.jobs.LevelUpEvent;
import com.careercoach.jobs.SseHub;

/**
 * Unit tests for the SSE emission side of {@link GamificationService#award}
 * (BACKEND_DESIGN §5 step 5 / issue-4): a granted award pushes {@code exp-gain}
 * (and {@code level-up} when the profile crosses a level) onto the global stream;
 * an idempotent replay emits nothing. Red phase: award does not emit yet.
 */
@ExtendWith(MockitoExtension.class)
class GamificationServiceSseTest {

    @Mock private SkillRepository skillRepository;
    @Mock private ExpEventRepository expEventRepository;
    @Mock private CareerProfileService careerProfileService;
    @Mock private GoalService goalService;
    @Mock private TaskTypeDefinitionService taskTypeDefinitionService;
    @Mock private SseHub sseHub;

    private GamificationService service;

    @BeforeEach
    void setUp() {
        service = new GamificationService(skillRepository, expEventRepository, careerProfileService,
                goalService, taskTypeDefinitionService, sseHub);
    }

    private static TaskTypeDefinition type(int expBase) {
        return new TaskTypeDefinition("AI_REVIEW", "AI review", VerificationMethod.AI_ARTIFACT_REVIEW,
                expBase, false, false);
    }

    private AwardCommand cmd() {
        return new AwardCommand(1L, 100L, 555L, "AI_REVIEW", 5L, "evaluation",
                List.of(new SkillAward("JAVA", 40)));
    }

    private void arrangeFreshAward(int newLevel) {
        when(expEventRepository.findBySourceTaskId(100L)).thenReturn(List.of());
        when(taskTypeDefinitionService.get("AI_REVIEW")).thenReturn(type(50));
        when(skillRepository.findById("JAVA")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(inv -> inv.getArgument(0));
        when(expEventRepository.save(any(ExpEvent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 0L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(anyLong(), anyLong()))
                .thenReturn(new CareerProfile(1L, 40L, newLevel, AvatarState.initial()));
    }

    @Test
    void award_shouldEmitExpGain_whenExpGrantedWithoutLevelUp() {
        // Arrange — profile stays at level 1
        arrangeFreshAward(1);

        // Act
        service.award(cmd());

        // Assert
        ArgumentCaptor<ExpGainEvent> captor = ArgumentCaptor.forClass(ExpGainEvent.class);
        verify(sseHub).emit(captor.capture());
        ExpGainEvent event = captor.getValue();
        assertThat(event.sourceTaskId()).isEqualTo(100L);
        assertThat(event.granted()).isEqualTo(40L);
        assertThat(event.totalExp()).isEqualTo(40L);
        verify(sseHub, never()).emit(any(LevelUpEvent.class));
    }

    @Test
    void award_shouldEmitExpGainAndLevelUp_whenProfileLevelsUp() {
        // Arrange — profile crosses into level 2
        arrangeFreshAward(2);

        // Act
        service.award(cmd());

        // Assert
        verify(sseHub).emit(any(ExpGainEvent.class));
        ArgumentCaptor<LevelUpEvent> captor = ArgumentCaptor.forClass(LevelUpEvent.class);
        verify(sseHub).emit(captor.capture());
        assertThat(captor.getValue().newLevel()).isEqualTo(2);
    }

    @Test
    void award_shouldEmitNothing_whenReplayIsIdempotent() {
        // Arrange — an existing event for the same (sourceTaskId, attemptId) → duplicate
        ExpEvent existing = ExpEvent.builder()
                .sourceTaskId(100L).attemptId(555L).skillKey("JAVA").amount(40L).reason("evaluation").build();
        when(expEventRepository.findBySourceTaskId(100L)).thenReturn(List.of(existing));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 40L, 1, AvatarState.initial()));
        when(skillRepository.findById("JAVA")).thenReturn(Optional.of(Skill.createNew("JAVA")));

        // Act
        service.award(cmd());

        // Assert — no new exp accrued, so no stream events
        verify(sseHub, never()).emit(any(ExpGainEvent.class));
        verify(sseHub, never()).emit(any(LevelUpEvent.class));
    }
}
