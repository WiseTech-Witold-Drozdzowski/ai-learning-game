package com.careercoach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;

/**
 * Unit tests for {@link CurrentUserService} (BACKEND_DESIGN §2.1) — resolves
 * the authenticated principal in the {@code SecurityContext} to the persistent
 * {@link User}. The repository is mocked; the {@code SecurityContext} is driven
 * directly. Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class CurrentUserServiceTest {

    private static final String EMAIL = "author@example.com";
    private static final String GOOGLE_SUB = "google-sub-123";

    @Mock
    private UserRepository userRepository;

    private CurrentUserService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnPersistentUser_whenPrincipalAuthenticated() {
        // Arrange — an authenticated Google principal carrying email + sub.
        service = new CurrentUserService(userRepository);
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", EMAIL, "sub", GOOGLE_SUB),
                "email");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));

        User user = new User(EMAIL, GOOGLE_SUB, "Author");
        // Lenient: the impl may resolve by email (the documented identity key) or by sub.
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        lenient().when(userRepository.findByGoogleSub(GOOGLE_SUB)).thenReturn(Optional.of(user));

        // Act
        User resolved = service.getCurrentUser();

        // Assert
        assertThat(resolved).isSameAs(user);
    }

    @Test
    void shouldThrow_whenAuthenticatedButUserNotPersisted() {
        // Arrange — an authenticated principal whose identity is absent from the store.
        service = new CurrentUserService(userRepository);
        OAuth2User principal = new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", EMAIL, "sub", GOOGLE_SUB),
                "email");
        SecurityContextHolder.getContext().setAuthentication(
                new OAuth2AuthenticationToken(principal, principal.getAuthorities(), "google"));
        // Lenient: the impl may resolve by email (the documented identity key) or by sub.
        lenient().when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        lenient().when(userRepository.findByGoogleSub(GOOGLE_SUB)).thenReturn(Optional.empty());

        // Act / Assert — an authenticated principal with no persisted User is an inconsistent state.
        assertThatThrownBy(() -> service.getCurrentUser())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldThrow_whenNoAuthentication() {
        // Arrange — no authentication in the context.
        service = new CurrentUserService(userRepository);
        SecurityContextHolder.clearContext();

        // Act / Assert
        assertThatThrownBy(() -> service.getCurrentUser())
                .isInstanceOf(IllegalStateException.class);
    }
}
