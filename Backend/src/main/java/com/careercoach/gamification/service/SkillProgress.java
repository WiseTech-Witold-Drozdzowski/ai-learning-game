package com.careercoach.gamification.service;

import lombok.Builder;

@Builder
public record SkillProgress(String skillKey, long exp, int level, long granted, boolean leveledUp) {
}
