package com.careercoach.gamification.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.gamification.domain.ExpEvent;

public interface ExpEventRepository extends JpaRepository<ExpEvent, Long> {

    List<ExpEvent> findBySourceTaskId(Long sourceTaskId);
}
