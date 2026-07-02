package com.careercoach.gamification.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic exp-to-level curve (issue-5, T3).
 */
class LevelCurveTest {

    @Test
    void shouldReturnLevelOne_whenExpIsZero() {
        // Act / Assert
        assertThat(LevelCurve.levelForExp(0L)).isEqualTo(1);
    }

    @Test
    void shouldReturnLevelOne_whenExpIsJustBelowThreshold() {
        // Act / Assert
        assertThat(LevelCurve.levelForExp(LevelCurve.EXP_PER_LEVEL - 1)).isEqualTo(1);
    }

    @Test
    void shouldReturnLevelTwo_whenExpIsExactlyAtThreshold() {
        // Act / Assert
        assertThat(LevelCurve.levelForExp(LevelCurve.EXP_PER_LEVEL)).isEqualTo(2);
    }

    @Test
    void shouldGrowMonotonically_whenExpIsLarge() {
        // Act / Assert
        assertThat(LevelCurve.levelForExp(550L)).isEqualTo(6);
    }

    @Test
    void shouldReturnLevelOne_whenExpIsNegative() {
        // Act / Assert
        assertThat(LevelCurve.levelForExp(-50L)).isEqualTo(1);
    }
}
