package com.careercoach.gamification.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.auth.service.CurrentUserService;
import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.ProfileResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileQueryService profileQueryService;
    private final CurrentUserService currentUserService;

    @GetMapping("/api/profile")
    public ProfileResponse profile() {
        Long userId = currentUserService.getCurrentUser().getId();
        return profileQueryService.getProfile(userId);
    }
}
