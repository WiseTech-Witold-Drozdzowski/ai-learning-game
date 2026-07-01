package com.careercoach.config.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.config.domain.TaskTypeDefinition;

public interface TaskTypeDefinitionRepository extends JpaRepository<TaskTypeDefinition, String> {
}
