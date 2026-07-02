package com.careercoach.gamification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.gamification.domain.Skill;

public interface SkillRepository extends JpaRepository<Skill, String> {

    List<Skill> findAllByOrderByKeyAsc();
}
