package com.careercoach.gamification.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.gamification.domain.ExpEvent;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.goals.service.GoalService;
import com.careercoach.tasks.service.TaskService;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class GamificationRebuildService {

    private final ExpEventRepository expEventRepository;
    private final SkillRepository skillRepository;
    private final GoalService goalService;
    private final CareerProfileService careerProfileService;
    private final TaskService taskService;

    public RebuildResult rebuild(Long userId) {
        List<ExpEvent> allEvents = expEventRepository.findAll();

        // Build per-skill sums (null-skillKey events excluded from skill branch)
        Map<String, Long> skillSums = new HashMap<>();
        long profileTotal = 0L;
        for (ExpEvent event : allEvents) {
            profileTotal += event.getAmount();
            if (event.getSkillKey() != null) {
                skillSums.merge(event.getSkillKey(), event.getAmount(), Long::sum);
            }
        }

        // Recompute skills: union of ledger keys and tracked skills
        Set<String> allSkillKeys = new HashSet<>(skillSums.keySet());
        Map<String, Skill> existing = new HashMap<>();
        for (Skill skill : skillRepository.findAll()) {
            allSkillKeys.add(skill.getKey());
            existing.put(skill.getKey(), skill);
        }

        for (String key : allSkillKeys) {
            long exp = skillSums.getOrDefault(key, 0L);
            Skill skill = existing.computeIfAbsent(key, Skill::createNew);
            skill.setExp(exp);
            skill.setLevel(LevelCurve.levelForExp(exp));
            skillRepository.save(skill);
        }

        // Recompute goals
        goalService.resetAllExpEarned();
        Map<Long, Long> taskToGoal = new HashMap<>();
        Set<Long> rebuiltGoals = new HashSet<>();
        for (ExpEvent event : allEvents) {
            Long goalId = taskToGoal.computeIfAbsent(
                    event.getSourceTaskId(), taskService::getGoalId);
            rebuiltGoals.add(goalId);
            goalService.bubbleExp(goalId, event.getAmount());
        }

        // Recompute profile
        var profile = careerProfileService.setTotalExp(userId, profileTotal);

        return new RebuildResult(allSkillKeys.size(), rebuiltGoals.size(), profile.getTotalExp(), profile.getLevel());
    }
}
