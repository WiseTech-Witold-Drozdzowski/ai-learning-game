package com.careercoach.gamification.config;

import org.mapstruct.factory.Mappers;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.careercoach.gamification.mapper.SkillMapper;

@Configuration
public class GamificationConfig {

    @Bean
    public SkillMapper skillMapper() {
        return Mappers.getMapper(SkillMapper.class);
    }
}
