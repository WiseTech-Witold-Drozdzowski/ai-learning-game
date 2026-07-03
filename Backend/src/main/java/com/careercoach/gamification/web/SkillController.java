package com.careercoach.gamification.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.SkillView;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SkillController {

    private final ProfileQueryService profileQueryService;

    @GetMapping("/api/skills")
    public List<SkillView> skills() {
        return profileQueryService.listSkills();
    }
}
