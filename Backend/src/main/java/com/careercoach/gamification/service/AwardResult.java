package com.careercoach.gamification.service;

import java.util.List;

import lombok.Builder;

@Builder
public record AwardResult(
        boolean applied,
        long totalGranted,
        long profileTotalExp,
        int profileLevel,
        boolean profileLeveledUp,
        List<SkillProgress> skills) {
}
