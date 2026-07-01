package com.careercoach.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EmailWhitelist#permits(String)} (TECHNICAL_DESIGN §7).
 * Pure logic — no Spring context. Red phase: the skeleton throws
 * {@code UnsupportedOperationException}.
 */
class EmailWhitelistTest {

    private static final String ALLOWED = "author@example.com";

    @Test
    void shouldPermit_whenEmailOnWhitelist() {
        // Arrange
        EmailWhitelist whitelist = new EmailWhitelist(List.of(ALLOWED, "second@example.com"));

        // Act
        boolean permitted = whitelist.permits(ALLOWED);

        // Assert
        assertThat(permitted).isTrue();
    }

    @Test
    void shouldReject_whenEmailNotOnWhitelist() {
        // Arrange
        EmailWhitelist whitelist = new EmailWhitelist(List.of(ALLOWED));

        // Act
        boolean permitted = whitelist.permits("intruder@example.com");

        // Assert
        assertThat(permitted).isFalse();
    }

    @Test
    void shouldReject_whenWhitelistEmpty() {
        // Arrange
        EmailWhitelist whitelist = new EmailWhitelist(List.of());

        // Act / Assert — nobody is allowed when the whitelist is empty.
        assertThat(whitelist.permits(ALLOWED)).isFalse();
    }

    @Test
    void shouldReject_whenEmailNull() {
        // Arrange
        EmailWhitelist whitelist = new EmailWhitelist(List.of(ALLOWED));

        // Act / Assert
        assertThat(whitelist.permits(null)).isFalse();
    }

    @Test
    void shouldReject_whenEmailBlank() {
        // Arrange
        EmailWhitelist whitelist = new EmailWhitelist(List.of(ALLOWED));

        // Act / Assert
        assertThat(whitelist.permits("   ")).isFalse();
    }
}
