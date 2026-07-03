package com.careercoach.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.domain.ExpEvent;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.goals.service.GoalService;
import com.careercoach.tasks.domain.exception.TaskNotFoundException;
import com.careercoach.tasks.service.TaskService;

/**
 * Unit tests for {@link GamificationRebuildService} (issue-7) — all collaborators are mocked.
 * Red phase: the skeleton throws {@code NotImplementedException}.
 */
@ExtendWith(MockitoExtension.class)
class GamificationRebuildServiceTest {

    @Mock
    private ExpEventRepository expEventRepository;

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private GoalService goalService;

    @Mock
    private CareerProfileService careerProfileService;

    @Mock
    private TaskService taskService;

    @Captor
    private ArgumentCaptor<Skill> skillCaptor;

    private GamificationRebuildService service;

    private Long userId = 1L;

    @BeforeEach
    void setUp() {
        service = new GamificationRebuildService(
                expEventRepository, skillRepository, goalService, careerProfileService, taskService);
    }

    private ExpEvent expEvent(Long sourceTaskId, Long attemptId, String skillKey, long amount, String reason) {
        return ExpEvent.builder()
                .sourceTaskId(sourceTaskId)
                .attemptId(attemptId)
                .skillKey(skillKey)
                .amount(amount)
                .reason(reason)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    void shouldRecomputeSkillExpAndLevelFromLedger_whenSingleSkillMultipleEvents() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, "JAVA", 30L, "task-complete");
        ExpEvent event2 = expEvent(101L, 2L, "JAVA", 20L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1, event2));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.setTotalExp(userId, 50L))
                .thenReturn(new CareerProfile(userId, 50L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(skillRepository).save(skillCaptor.capture());
        Skill saved = skillCaptor.getValue();
        assertThat(saved.getKey()).isEqualTo("JAVA");
        assertThat(saved.getExp()).isEqualTo(50L);
        assertThat(saved.getLevel()).isEqualTo(LevelCurve.levelForExp(50L));
    }

    @Test
    void shouldSumAmountsPerSkillIndependently_whenMultipleSkills() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, "JAVA", 30L, "task-complete");
        ExpEvent event2 = expEvent(101L, 2L, "SPRING", 15L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1, event2));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.setTotalExp(userId, 45L))
                .thenReturn(new CareerProfile(userId, 45L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(skillRepository, times(2)).save(skillCaptor.capture());
        List<Skill> allSaves = skillCaptor.getAllValues();
        assertThat(allSaves).hasSize(2);

        Skill java = allSaves.stream().filter(s -> s.getKey().equals("JAVA")).findFirst().orElseThrow();
        assertThat(java.getExp()).isEqualTo(30L);
        assertThat(java.getLevel()).isEqualTo(LevelCurve.levelForExp(30L));

        Skill spring = allSaves.stream().filter(s -> s.getKey().equals("SPRING")).findFirst().orElseThrow();
        assertThat(spring.getExp()).isEqualTo(15L);
        assertThat(spring.getLevel()).isEqualTo(LevelCurve.levelForExp(15L));
    }

    @Test
    void shouldSetSkillLevelFromCurrentCurveIgnoringStoredLevel_whenStoredLevelStale() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, "JAVA", 30L, "task-complete");
        ExpEvent event2 = expEvent(101L, 2L, "JAVA", 20L, "task-complete");
        Skill existing = Skill.builder().key("JAVA").exp(999L).level(99).build(); // stale exp/level
        when(expEventRepository.findAll()).thenReturn(List.of(event1, event2));
        when(skillRepository.findAll()).thenReturn(List.of(existing));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.setTotalExp(userId, 50L))
                .thenReturn(new CareerProfile(userId, 50L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(skillRepository).save(skillCaptor.capture());
        Skill saved = skillCaptor.getValue();
        assertThat(saved.getKey()).isEqualTo("JAVA");
        assertThat(saved.getExp()).isEqualTo(50L);
        assertThat(saved.getLevel()).isEqualTo(LevelCurve.levelForExp(50L));
        assertThat(saved.getLevel()).isNotEqualTo(99);
    }

    @Test
    void shouldResetSkillsWithNoLedgerEventsToZero_whenTrackedButAbsentFromLedger() {
        // Arrange
        Skill python = Skill.builder().key("PYTHON").exp(100L).level(2).build();
        when(expEventRepository.findAll()).thenReturn(List.of());
        when(skillRepository.findAll()).thenReturn(List.of(python));
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.setTotalExp(userId, 0L))
                .thenReturn(new CareerProfile(userId, 0L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(skillRepository).save(skillCaptor.capture());
        Skill saved = skillCaptor.getValue();
        assertThat(saved.getKey()).isEqualTo("PYTHON");
        assertThat(saved.getExp()).isEqualTo(0L);
        assertThat(saved.getLevel()).isEqualTo(1);
    }

    @Test
    void shouldResetAllGoalsThenBubbleEachEventAmount() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, "JAVA", 10L, "task-complete");
        ExpEvent event2 = expEvent(101L, 2L, "JAVA", 15L, "task-complete");
        ExpEvent event3 = expEvent(102L, 3L, "SPRING", 5L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1, event2, event3));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskService.getGoalId(100L)).thenReturn(10L);
        when(taskService.getGoalId(101L)).thenReturn(10L);
        when(taskService.getGoalId(102L)).thenReturn(20L);
        when(careerProfileService.setTotalExp(userId, 30L))
                .thenReturn(new CareerProfile(userId, 30L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(goalService).resetAllExpEarned();
        verify(goalService).bubbleExp(10L, 10L);
        verify(goalService).bubbleExp(10L, 15L);
        verify(goalService).bubbleExp(20L, 5L);
    }

    @Test
    void shouldSetProfileTotalToSumOfAllEventAmountsIncludingNullSkill() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, null, 7L, "general-exp");
        ExpEvent event2 = expEvent(101L, 2L, "JAVA", 10L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1, event2));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskService.getGoalId(100L)).thenReturn(10L);
        when(taskService.getGoalId(101L)).thenReturn(10L);
        when(careerProfileService.setTotalExp(userId, 17L))
                .thenReturn(new CareerProfile(userId, 17L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(careerProfileService).setTotalExp(userId, 17L);
    }

    @Test
    void shouldExcludeNullSkillEventsFromSkillRecompute_butIncludeInProfileAndGoal() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, null, 7L, "general-exp");
        ExpEvent event2 = expEvent(101L, 2L, "JAVA", 10L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1, event2));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskService.getGoalId(100L)).thenReturn(10L);
        when(taskService.getGoalId(101L)).thenReturn(10L);
        when(careerProfileService.setTotalExp(userId, 17L))
                .thenReturn(new CareerProfile(userId, 17L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(skillRepository).save(skillCaptor.capture());
        Skill saved = skillCaptor.getValue();
        assertThat(saved.getKey()).isEqualTo("JAVA"); // null-skill not saved
        assertThat(saved.getExp()).isEqualTo(10L);

        verify(goalService).bubbleExp(10L, 7L); // null-skill event still bubbled
        verify(goalService).bubbleExp(10L, 10L);
        verify(careerProfileService).setTotalExp(userId, 17L); // includes null-skill
    }

    @Test
    void shouldProduceZeroProfileAndCreateNoEvents_whenLedgerEmpty() {
        // Arrange
        when(expEventRepository.findAll()).thenReturn(List.of());
        when(skillRepository.findAll()).thenReturn(List.of());
        when(careerProfileService.setTotalExp(userId, 0L))
                .thenReturn(new CareerProfile(userId, 0L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(careerProfileService).setTotalExp(userId, 0L);
        verify(goalService).resetAllExpEarned();
        verify(goalService, never()).bubbleExp(anyLong(), anyLong());
        verify(skillRepository, never()).save(any(Skill.class));
    }

    @Test
    void shouldNeverWriteExpEvents_whenRebuilding() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, "JAVA", 30L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskService.getGoalId(100L)).thenReturn(10L);
        when(careerProfileService.setTotalExp(userId, 30L))
                .thenReturn(new CareerProfile(userId, 30L, 1, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        verify(expEventRepository, never()).save(any(ExpEvent.class));
        verify(expEventRepository, never()).delete(any(ExpEvent.class));
    }

    @Test
    void shouldReturnRebuildResultWithProfileTotalsFromCurve_whenTotalCrossesLevelThreshold() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, "JAVA", 250L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskService.getGoalId(100L)).thenReturn(10L);
        int expectedLevel = LevelCurve.levelForExp(250L);
        when(careerProfileService.setTotalExp(userId, 250L))
                .thenReturn(new CareerProfile(userId, 250L, expectedLevel, AvatarState.initial()));

        // Act
        RebuildResult result = service.rebuild(userId);

        // Assert
        assertThat(result.profileTotalExp()).isEqualTo(250L);
        assertThat(result.profileLevel()).isEqualTo(expectedLevel);
    }

    @Test
    void shouldPropagateTaskNotFound_whenEventReferencesMissingTask() {
        // Arrange
        ExpEvent event1 = expEvent(100L, 1L, "JAVA", 30L, "task-complete");
        when(expEventRepository.findAll()).thenReturn(List.of(event1));
        when(skillRepository.findAll()).thenReturn(List.of());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(taskService.getGoalId(100L)).thenThrow(new TaskNotFoundException("Task not found"));

        // Act / Assert
        assertThatThrownBy(() -> service.rebuild(userId))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
