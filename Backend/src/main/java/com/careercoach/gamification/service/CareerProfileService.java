package com.careercoach.gamification.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class CareerProfileService {

    private final CareerProfileRepository careerProfileRepository;

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

    public CareerProfile addExp(Long userId, long amount) {
        CareerProfile profile = careerProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No career profile provisioned for user " + userId));
        profile.setTotalExp(profile.getTotalExp() + amount);
        profile.setLevel(LevelCurve.levelForExp(profile.getTotalExp()));
        return careerProfileRepository.save(profile);
    }

    public CareerProfile setTotalExp(Long userId, long totalExp) {
        CareerProfile profile = careerProfileRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("No career profile provisioned for user " + userId));
        profile.setTotalExp(totalExp);
        profile.setLevel(LevelCurve.levelForExp(totalExp));
        return careerProfileRepository.save(profile);
    }
}
