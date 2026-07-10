package com.careercoach.gamification.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.domain.ExpEvent;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.goals.service.GoalService;
import com.careercoach.jobs.ExpGainEvent;
import com.careercoach.jobs.LevelUpEvent;
import com.careercoach.jobs.SseHub;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class GamificationService {

    private final SkillRepository skillRepository;
    private final ExpEventRepository expEventRepository;
    private final CareerProfileService careerProfileService;
    private final GoalService goalService;
    private final TaskTypeDefinitionService taskTypeDefinitionService;
    private final SseHub sseHub;


    @Transactional
    public AwardResult award(AwardCommand cmd) {
        List<ExpEvent> matching = expEventRepository.findBySourceTaskId(cmd.sourceTaskId()).stream()
                .filter(event -> Objects.equals(event.getAttemptId(), cmd.attemptId()))
                .toList();
        if (!matching.isEmpty()) {
            return buildDuplicateResult(cmd, matching);
        }

        TaskTypeDefinition typeDefinition = taskTypeDefinitionService.get(cmd.typeKey());
        int expBase = typeDefinition.getExpBase();

        long totalGranted = 0L;
        List<SkillProgress> skillProgresses = new ArrayList<>();
        for (SkillAward skillAward : cmd.skillAwards()) {
            long granted = Math.min(Math.max(skillAward.expProposed(), 0), expBase);
            totalGranted += granted;

            ExpEvent event = ExpEvent.builder()
                    .sourceTaskId(cmd.sourceTaskId())
                    .attemptId(cmd.attemptId())
                    .skillKey(skillAward.skillKey())
                    .amount(granted)
                    .reason(cmd.reason())
                    .build();
            expEventRepository.save(event);

            Skill skill = skillRepository.findById(skillAward.skillKey())
                    .orElseGet(() -> Skill.createNew(skillAward.skillKey()));
            int oldLevel = skill.getLevel();
            skill.setExp(skill.getExp() + granted);
            skill.setLevel(LevelCurve.levelForExp(skill.getExp()));
            skillRepository.save(skill);

            skillProgresses.add(new SkillProgress(
                    skillAward.skillKey(), skill.getExp(), skill.getLevel(), granted, skill.getLevel() > oldLevel));
        }

        goalService.bubbleExp(cmd.goalId(), totalGranted);

        int oldProfileLevel = careerProfileService.getForUser(cmd.userId()).getLevel();
        CareerProfile profile = careerProfileService.addExp(cmd.userId(), totalGranted);

        AwardResult result = AwardResult.builder()
                .applied(true)
                .totalGranted(totalGranted)
                .profileTotalExp(profile.getTotalExp())
                .profileLevel(profile.getLevel())
                .profileLeveledUp(profile.getLevel() > oldProfileLevel)
                .skills(skillProgresses)
                .build();

        emitProgress(cmd, result);
        return result;
    }

    /** Push the accrual onto the global SSE stream (BACKEND_DESIGN §5 step 5). */
    private void emitProgress(AwardCommand cmd, AwardResult result) {
        if (result.totalGranted() > 0) {
            sseHub.emit(new ExpGainEvent(
                    cmd.sourceTaskId(), result.totalGranted(), result.profileTotalExp(), result.profileLevel()));
        }
        if (result.profileLeveledUp()) {
            sseHub.emit(new LevelUpEvent(cmd.sourceTaskId(), result.profileLevel()));
        }
    }

    private AwardResult buildDuplicateResult(AwardCommand cmd, List<ExpEvent> matching) {
        long totalGranted = matching.stream().mapToLong(ExpEvent::getAmount).sum();
        CareerProfile profile = careerProfileService.getForUser(cmd.userId());

        List<SkillProgress> skillProgresses = matching.stream()
                .filter(event -> event.getSkillKey() != null)
                .map(event -> {
                    Skill skill = skillRepository.findById(event.getSkillKey())
                            .orElseGet(() -> Skill.createNew(event.getSkillKey()));
                    return SkillProgress.builder()
                            .skillKey(event.getSkillKey())
                            .exp(skill.getExp())
                            .level(skill.getLevel())
                            .granted(event.getAmount())
                            .leveledUp(false)
                            .build();
                })
                .toList();

        return AwardResult.builder()
                .applied(false)
                .totalGranted(totalGranted)
                .profileTotalExp(profile.getTotalExp())
                .profileLevel(profile.getLevel())
                .profileLeveledUp(false)
                .skills(skillProgresses)
                .build();
    }
}
