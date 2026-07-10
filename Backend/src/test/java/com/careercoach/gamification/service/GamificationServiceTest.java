package com.careercoach.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.ConfigEntryNotFoundException;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.domain.ExpEvent;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.goals.service.GoalService;
import com.careercoach.jobs.SseHub;

/**
 * Unit tests for {@link GamificationService} (issue-5) — all collaborators are mocked.
 * Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class GamificationServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private ExpEventRepository expEventRepository;

    @Mock
    private CareerProfileService careerProfileService;

    @Mock
    private GoalService goalService;

    @Mock
    private TaskTypeDefinitionService taskTypeDefinitionService;

    @Mock
    private SseHub sseHub;

    private GamificationService service;

    @BeforeEach
    void setUp() {
        service = new GamificationService(skillRepository, expEventRepository, careerProfileService,
                goalService, taskTypeDefinitionService, sseHub);
    }

    private TaskTypeDefinition honorCheckWithExpBase(int expBase) {
        return new TaskTypeDefinition("HONOR_CHECK", "Honor check", VerificationMethod.HONOR, expBase, false, false);
    }

    @Test
    void shouldWriteOneExpEventAndUpdateCounters_whenSingleSkillAwarded() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 100L, 10L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheckWithExpBase(50));
        when(expEventRepository.findBySourceTaskId(100L)).thenReturn(List.of());
        when(skillRepository.findById("JAVA")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<ExpEvent> eventCaptor = ArgumentCaptor.forClass(ExpEvent.class);
        when(expEventRepository.save(eventCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 0L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(1L, 10L))
                .thenReturn(new CareerProfile(1L, 10L, 1, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(eventCaptor.getAllValues()).hasSize(1);
        ExpEvent savedEvent = eventCaptor.getValue();
        assertThat(savedEvent.getSourceTaskId()).isEqualTo(100L);
        assertThat(savedEvent.getAttemptId()).isEqualTo(10L);
        assertThat(savedEvent.getSkillKey()).isEqualTo("JAVA");
        assertThat(savedEvent.getAmount()).isEqualTo(10L);
        assertThat(savedEvent.getReason()).isEqualTo("task-complete");
        verify(goalService).bubbleExp(5L, 10L);
        verify(careerProfileService).addExp(1L, 10L);
        assertThat(result.applied()).isTrue();
        assertThat(result.totalGranted()).isEqualTo(10L);
    }

    @Test
    void shouldWriteTwoExpEventsAndSumGrantedAmounts_whenTwoSkillsAwarded() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 200L, 20L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10), new SkillAward("SPRING", 15)));
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheckWithExpBase(50));
        when(expEventRepository.findBySourceTaskId(200L)).thenReturn(List.of());
        when(skillRepository.findById("JAVA")).thenReturn(Optional.empty());
        when(skillRepository.findById("SPRING")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<ExpEvent> eventCaptor = ArgumentCaptor.forClass(ExpEvent.class);
        when(expEventRepository.save(eventCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 0L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(1L, 25L))
                .thenReturn(new CareerProfile(1L, 25L, 1, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(eventCaptor.getAllValues()).hasSize(2);
        verify(goalService).bubbleExp(5L, 25L);
        verify(careerProfileService).addExp(1L, 25L);
        assertThat(result.totalGranted()).isEqualTo(25L);
    }

    @Test
    void shouldClampGrantedAmountToExpBase_whenProposedAboveTypeLimit() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 300L, 30L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 999)));
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheckWithExpBase(10));
        when(expEventRepository.findBySourceTaskId(300L)).thenReturn(List.of());
        when(skillRepository.findById("JAVA")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<ExpEvent> eventCaptor = ArgumentCaptor.forClass(ExpEvent.class);
        when(expEventRepository.save(eventCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 0L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(1L, 10L))
                .thenReturn(new CareerProfile(1L, 10L, 1, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(eventCaptor.getValue().getAmount()).isEqualTo(10L);
        assertThat(result.totalGranted()).isEqualTo(10L);
        verify(goalService).bubbleExp(5L, 10L);
        verify(careerProfileService).addExp(1L, 10L);
    }

    @Test
    void shouldSkipWritesAndReturnApplyFalse_whenSameSourceTaskAndAttemptIdRetried() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 400L, 40L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        ExpEvent existing = ExpEvent.builder()
                .id(1L).sourceTaskId(400L).attemptId(40L).skillKey("JAVA").amount(10L).reason("task-complete")
                .build();
        when(expEventRepository.findBySourceTaskId(400L)).thenReturn(List.of(existing));
        lenient().when(skillRepository.findById("JAVA"))
                .thenReturn(Optional.of(Skill.builder().key("JAVA").level(1).exp(10L).build()));
        lenient().when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 10L, 1, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(result.applied()).isFalse();
        verify(expEventRepository, never()).save(any(ExpEvent.class));
        verify(goalService, never()).bubbleExp(any(), anyLong());
        verify(careerProfileService, never()).addExp(any(), anyLong());
    }

    @Test
    void shouldWriteNewEventsAndUpdateCounters_whenRetriedWithDifferentAttemptId() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 500L, 51L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        ExpEvent existing = ExpEvent.builder()
                .id(1L).sourceTaskId(500L).attemptId(50L).skillKey("JAVA").amount(10L).reason("task-complete")
                .build();
        when(expEventRepository.findBySourceTaskId(500L)).thenReturn(List.of(existing));
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheckWithExpBase(50));
        when(skillRepository.findById("JAVA")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(expEventRepository.save(any(ExpEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 10L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(1L, 10L))
                .thenReturn(new CareerProfile(1L, 20L, 1, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(result.applied()).isTrue();
        verify(expEventRepository).save(any(ExpEvent.class));
        verify(goalService).bubbleExp(5L, 10L);
        verify(careerProfileService).addExp(1L, 10L);
    }

    @Test
    void shouldTreatNullAttemptIdsAsMatching_whenIdempotencyChecked() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 600L, null, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        ExpEvent existing = ExpEvent.builder()
                .id(1L).sourceTaskId(600L).attemptId(null).skillKey("JAVA").amount(10L).reason("task-complete")
                .build();
        when(expEventRepository.findBySourceTaskId(600L)).thenReturn(List.of(existing));
        lenient().when(skillRepository.findById("JAVA"))
                .thenReturn(Optional.of(Skill.builder().key("JAVA").level(1).exp(10L).build()));
        lenient().when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 10L, 1, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(result.applied()).isFalse();
        verify(expEventRepository, never()).save(any(ExpEvent.class));
        verify(goalService, never()).bubbleExp(any(), anyLong());
        verify(careerProfileService, never()).addExp(any(), anyLong());
    }

    @Test
    void shouldRecomputeSkillLevelAndFlagLevelUp_whenSkillExpCrossesThreshold() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 700L, 70L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheckWithExpBase(50));
        when(expEventRepository.findBySourceTaskId(700L)).thenReturn(List.of());
        when(skillRepository.findById("JAVA"))
                .thenReturn(Optional.of(Skill.builder().key("JAVA").level(1).exp(95L).build()));
        ArgumentCaptor<Skill> skillCaptor = ArgumentCaptor.forClass(Skill.class);
        when(skillRepository.save(skillCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(expEventRepository.save(any(ExpEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 95L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(1L, 10L))
                .thenReturn(new CareerProfile(1L, 105L, 2, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(skillCaptor.getValue().getExp()).isEqualTo(105L);
        assertThat(skillCaptor.getValue().getLevel()).isEqualTo(2);
        assertThat(result.skills()).hasSize(1);
        assertThat(result.skills().get(0).leveledUp()).isTrue();
        assertThat(result.skills().get(0).level()).isEqualTo(2);
    }

    @Test
    void shouldFlagProfileLeveledUp_whenAddExpRaisesProfileLevel() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 800L, 80L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheckWithExpBase(50));
        when(expEventRepository.findBySourceTaskId(800L)).thenReturn(List.of());
        when(skillRepository.findById("JAVA")).thenReturn(Optional.empty());
        when(skillRepository.save(any(Skill.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(expEventRepository.save(any(ExpEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 95L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(1L, 10L))
                .thenReturn(new CareerProfile(1L, 105L, 2, AvatarState.initial()));

        // Act
        AwardResult result = service.award(cmd);

        // Assert
        assertThat(result.profileLeveledUp()).isTrue();
        assertThat(result.profileLevel()).isEqualTo(2);
        assertThat(result.profileTotalExp()).isEqualTo(105L);
    }

    @Test
    void shouldCreateNewSkillAtLevelOne_whenSkillNotYetTracked() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 900L, 90L, "HONOR_CHECK", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheckWithExpBase(50));
        when(expEventRepository.findBySourceTaskId(900L)).thenReturn(List.of());
        when(skillRepository.findById("JAVA")).thenReturn(Optional.empty());
        ArgumentCaptor<Skill> skillCaptor = ArgumentCaptor.forClass(Skill.class);
        when(skillRepository.save(skillCaptor.capture())).thenAnswer(invocation -> invocation.getArgument(0));
        when(expEventRepository.save(any(ExpEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(careerProfileService.getForUser(1L))
                .thenReturn(new CareerProfile(1L, 0L, 1, AvatarState.initial()));
        when(careerProfileService.addExp(1L, 10L))
                .thenReturn(new CareerProfile(1L, 10L, 1, AvatarState.initial()));

        // Act
        service.award(cmd);

        // Assert
        assertThat(skillCaptor.getValue().getKey()).isEqualTo("JAVA");
        assertThat(skillCaptor.getValue().getExp()).isEqualTo(10L);
        assertThat(skillCaptor.getValue().getLevel()).isEqualTo(1);
    }

    @Test
    void shouldPropagateAndWriteNothing_whenTypeKeyUnknown() {
        // Arrange
        AwardCommand cmd = new AwardCommand(1L, 1000L, 100L, "UNKNOWN_TYPE", 5L, "task-complete",
                List.of(new SkillAward("JAVA", 10)));
        when(expEventRepository.findBySourceTaskId(1000L)).thenReturn(List.of());
        when(taskTypeDefinitionService.get("UNKNOWN_TYPE"))
                .thenThrow(new ConfigEntryNotFoundException("No task type definition for key: UNKNOWN_TYPE"));

        // Act / Assert
        assertThatThrownBy(() -> service.award(cmd)).isInstanceOf(ConfigEntryNotFoundException.class);
        verify(expEventRepository, never()).save(any(ExpEvent.class));
        verify(goalService, never()).bubbleExp(any(), anyLong());
        verify(careerProfileService, never()).addExp(any(), anyLong());
    }
}
