package com.careercoach.auth.web;

import com.careercoach.gamification.domain.AvatarState;

public record MeResponse(Long id, String email, String displayName, ProfileSummary profile) {

    public record ProfileSummary(long totalExp, int level, AvatarState avatarState) {
    }
}
