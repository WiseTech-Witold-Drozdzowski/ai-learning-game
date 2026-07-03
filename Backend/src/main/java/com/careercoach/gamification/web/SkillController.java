package com.careercoach.gamification.web;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.ExpEventView;
import com.careercoach.gamification.web.model.SkillView;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class SkillController {

    private final ProfileQueryService profileQueryService;

    @GetMapping("/skills")
    public List<SkillView> skills() {
        return profileQueryService.listSkills();
    }

    // Single-user app: no CurrentUserService/user filter needed — every ledger row
    // belongs to the one user, so returning the skill's whole history satisfies "tylko dane usera".
    @GetMapping("/skills/{key}/history")
    public List<ExpEventView> skillHistory(@PathVariable String key) {
        return profileQueryService.listSkillHistory(key);
    }
}
