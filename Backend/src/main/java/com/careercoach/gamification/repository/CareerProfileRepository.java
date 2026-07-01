package com.careercoach.gamification.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.gamification.domain.CareerProfile;

public interface CareerProfileRepository extends JpaRepository<CareerProfile, Long> {
}
