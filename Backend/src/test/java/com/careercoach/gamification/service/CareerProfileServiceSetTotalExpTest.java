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
 * Unit tests for {@link CareerProfileService#setTotalExp} (issue-7) — repository is mocked.
 * Red phase: the skeleton throws {@code NotImplementedException}.
 */
@ExtendWith(MockitoExtension.class)
class CareerProfileServiceSetTotalExpTest {

    @Mock
    private CareerProfileRepository careerProfileRepository;

    private CareerProfileService service;

    @BeforeEach
    void setUp() {
        service = new CareerProfileService(careerProfileRepository);
    }

    @Test
    void shouldSetAbsoluteTotalAndRecomputeLevel_whenProfileExists() {
        // Arrange
        CareerProfile existing = new CareerProfile(1L, 0L, 1, AvatarState.initial());
        when(careerProfileRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerProfileRepository.save(any(CareerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CareerProfile result = service.setTotalExp(1L, 250L);

        // Assert
        assertThat(result.getTotalExp()).isEqualTo(250L);
        assertThat(result.getLevel()).isEqualTo(LevelCurve.levelForExp(250L));
    }

    @Test
    void shouldSetZeroTotalAndLevelOne_whenTotalIsZero() {
        // Arrange
        CareerProfile existing = new CareerProfile(1L, 100L, 2, AvatarState.initial());
        when(careerProfileRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(careerProfileRepository.save(any(CareerProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        CareerProfile result = service.setTotalExp(1L, 0L);

        // Assert
        assertThat(result.getTotalExp()).isEqualTo(0L);
        assertThat(result.getLevel()).isEqualTo(1);
    }

    @Test
    void shouldThrowIllegalState_whenProfileMissing() {
        // Arrange
        when(careerProfileRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.setTotalExp(99L, 100L))
                .isInstanceOf(IllegalStateException.class);
    }
}
