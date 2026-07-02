package com.careercoach.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;

/**
 * Unit tests for {@link CareerProfileService#addExp} (issue-5) — repository is mocked.
 * Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class CareerProfileServiceAddExpTest {

    @Mock
    private CareerProfileRepository careerProfileRepository;

    private CareerProfileService service;

    @BeforeEach
    void setUp() {
        service = new CareerProfileService(careerProfileRepository);
    }

    @Test
    void shouldIncrementTotalExpAndRecomputeLevel_whenProfileExists() {
        // Arrange
        CareerProfile existing = new CareerProfile(1L, 10L, 1, AvatarState.initial());
        when(careerProfileRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerProfileRepository.save(any(CareerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CareerProfile result = service.addExp(1L, 15L);

        // Assert
        assertThat(result.getTotalExp()).isEqualTo(25L);
        assertThat(result.getLevel()).isEqualTo(1);
    }

    @Test
    void shouldRaiseLevel_whenAddedExpCrossesThreshold() {
        // Arrange
        CareerProfile existing = new CareerProfile(1L, 90L, 1, AvatarState.initial());
        when(careerProfileRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerProfileRepository.save(any(CareerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CareerProfile result = service.addExp(1L, 20L);

        // Assert
        assertThat(result.getTotalExp()).isEqualTo(110L);
        assertThat(result.getLevel()).isEqualTo(2);
    }

    @Test
    void shouldThrowIllegalStateException_whenNoProfileForUser() {
        // Arrange
        when(careerProfileRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.addExp(99L, 10L))
                .isInstanceOf(IllegalStateException.class);
    }
}
