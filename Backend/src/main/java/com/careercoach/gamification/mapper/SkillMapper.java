package com.careercoach.gamification.mapper;

import org.mapstruct.Mapper;

import com.careercoach.gamification.domain.Skill;
import com.careercoach.gamification.web.model.SkillView;

@Mapper
public interface SkillMapper {

    SkillView toSkillView(Skill skill, String displayName);
}
