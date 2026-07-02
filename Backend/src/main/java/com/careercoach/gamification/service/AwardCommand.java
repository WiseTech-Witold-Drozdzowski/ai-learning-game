package com.careercoach.gamification.service;

import java.util.List;

public record AwardCommand(
        Long userId,
        Long sourceTaskId,
        Long attemptId,
        String typeKey,
        Long goalId,
        String reason,
        List<SkillAward> skillAwards) {
}
