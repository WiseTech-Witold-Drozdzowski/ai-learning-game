package com.careercoach.gamification.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.mapper.SkillMapper;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.gamification.web.model.ExpEventView;
import com.careercoach.gamification.web.model.ProfileResponse;
import com.careercoach.gamification.web.model.SkillView;

import lombok.RequiredArgsConstructor;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ProfileQueryService {

    private final CareerProfileService careerProfileService;
    private final SkillRepository skillRepository;
    private final SkillDefinitionRepository skillDefinitionRepository;
    private final SkillMapper skillMapper;
    private final ExpEventRepository expEventRepository;

    public ProfileResponse getProfile(Long userId) {
        CareerProfile profile = careerProfileService.getForUser(userId);
        return new ProfileResponse(profile.getTotalExp(), profile.getLevel(), profile.getAvatarState(), listSkills());
    }

    public List<SkillView> listSkills() {
        Map<String, String> displayNames = skillDefinitionRepository.findAll().stream()
                .collect(Collectors.toMap(SkillDefinition::getKey, SkillDefinition::getDisplayName));
        return skillRepository.findAllByOrderByKeyAsc().stream()
                .map(skill -> toSkillView(skill, displayNames))
                .toList();
    }

    private SkillView toSkillView(Skill skill, Map<String, String> displayNames) {
        String displayName = displayNames.getOrDefault(skill.getKey(), skill.getKey());
        return skillMapper.toSkillView(skill, displayName);
    }

    // Single-user app: no user filter needed — all ledger rows belong to the one user,
    // so the skill's full chronological history is exactly "tylko dane usera".
    public List<ExpEventView> listSkillHistory(String skillKey) {
        return expEventRepository.findBySkillKeyOrderByCreatedAtAsc(skillKey).stream()
                .map(skillMapper::toExpEventView)
                .toList();
    }
}
