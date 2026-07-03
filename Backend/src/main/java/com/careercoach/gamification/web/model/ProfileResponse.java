package com.careercoach.gamification.web.model;

import java.util.List;

import com.careercoach.gamification.domain.AvatarState;

public record ProfileResponse(long totalExp, int level, AvatarState avatarState, List<SkillView> skills) {
}
