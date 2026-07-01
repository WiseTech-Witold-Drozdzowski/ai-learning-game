package com.careercoach.gamification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CareerProfileService {

    private final CareerProfileRepository careerProfileRepository;

    @Transactional
    public CareerProfile getOrCreate(Long userId) {
        return careerProfileRepository.findById(userId)
                .orElseGet(() -> careerProfileRepository.save(
                        new CareerProfile(userId, 0L, 1, AvatarState.initial())));
    }

    @Transactional(readOnly = true)
    public CareerProfile getForUser(Long userId) {
        return careerProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No career profile provisioned for user " + userId));
    }
}
