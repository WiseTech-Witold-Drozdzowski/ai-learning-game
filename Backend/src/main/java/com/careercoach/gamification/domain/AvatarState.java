package com.careercoach.gamification.domain;

import java.util.List;

public record AvatarState(AvatarTier tier, List<String> unlockedAttributes) {

    public static AvatarState initial() {
        return new AvatarState(AvatarTier.BRONZE, List.of());
    }
}
