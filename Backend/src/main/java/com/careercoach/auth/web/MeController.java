package com.careercoach.auth.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.auth.domain.User;
import com.careercoach.auth.service.CurrentUserService;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.service.CareerProfileService;

@RestController
public class MeController {

    private final CurrentUserService currentUserService;
    private final CareerProfileService careerProfileService;

    public MeController(CurrentUserService currentUserService,
                        CareerProfileService careerProfileService) {
        this.currentUserService = currentUserService;
        this.careerProfileService = careerProfileService;
    }

    @GetMapping("/me")
    public MeResponse me() {
        User user = currentUserService.getCurrentUser();
        CareerProfile profile = careerProfileService.getOrCreate(user.getId());
        return new MeResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                new MeResponse.ProfileSummary(
                        profile.getTotalExp(), profile.getLevel(), profile.getAvatarState()));
    }
}
