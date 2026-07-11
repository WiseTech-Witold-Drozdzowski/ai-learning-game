package com.careercoach.goals.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.goals.domain.Goal;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findAllByOrderByOrderIndexAscIdAsc();

    long countByParentIdIsNull();

    long countByParentId(Long parentId);
}
