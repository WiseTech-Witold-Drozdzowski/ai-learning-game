package com.careercoach.config.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.config.domain.SkillDefinition;

public interface SkillDefinitionRepository extends JpaRepository<SkillDefinition, String> {
}
