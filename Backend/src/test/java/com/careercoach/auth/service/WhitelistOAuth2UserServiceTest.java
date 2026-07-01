package com.careercoach.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.careercoach.auth.domain.EmailWhitelist;
import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.gamification.service.CareerProfileService;

/**
 * Unit tests for {@link WhitelistOAuth2UserService#loadUser(OAuth2UserRequest)}
 * (TECHNICAL_DESIGN §7) — the OAuth2 login seam that enforces the email
 * whitelist and auto-provisions the local {@link User} + seed career profile on
 * first login. The raw Google load is driven through the injected delegate
 * {@link OAuth2UserService}; the repository and profile service are mocked.
 * Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class WhitelistOAuth2UserServiceTest {

    private static final String ALLOWED_EMAIL = "author@example.com";
    private static final String BLOCKED_EMAIL = "intruder@example.com";
    private static final String GOOGLE_SUB = "google-sub-123";

    @Mock
    private UserRepository userRepository;

    @Mock
    private CareerProfileService careerProfileService;

    @Mock
    private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

    @Mock
    private OAuth2UserRequest userRequest;

    private final EmailWhitelist whitelist = new EmailWhitelist(List.of(ALLOWED_EMAIL));

    private WhitelistOAuth2UserService service() {
        return new WhitelistOAuth2UserService(userRepository, careerProfileService, whitelist, delegate);
    }

    private OAuth2User googlePrincipal(String email) {
        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                Map.of("email", email, "sub", GOOGLE_SUB),
                "email");
    }

    @Test
    void shouldRejectAndNotProvision_whenEmailNotWhitelisted() {
        // Arrange — Google returns a principal whose email is NOT on the whitelist.
        lenient().when(delegate.loadUser(userRequest)).thenReturn(googlePrincipal(BLOCKED_EMAIL));

        // Act / Assert — the login is rejected at the auth boundary.
        assertThatThrownBy(() -> service().loadUser(userRequest))
                .isInstanceOf(OAuth2AuthenticationException.class);

        // Assert — no identity nor profile is provisioned for a rejected login.
        verify(userRepository, never()).save(any());
        verify(careerProfileService, never()).getOrCreate(anyLong());
    }

    @Test
    void shouldProvisionUserAndProfile_whenWhitelistedFirstLogin() {
        // Arrange — whitelisted email, no local identity yet (first login).
        User provisioned = mock(User.class);
        lenient().when(provisioned.getId()).thenReturn(1L);
        lenient().when(delegate.loadUser(userRequest)).thenReturn(googlePrincipal(ALLOWED_EMAIL));
        lenient().when(userRepository.findByEmail(ALLOWED_EMAIL)).thenReturn(Optional.empty());
        lenient().when(userRepository.save(any(User.class))).thenReturn(provisioned);

        // Act
        OAuth2User result = service().loadUser(userRequest);

        // Assert — a local identity and a seed profile are created on first login.
        verify(userRepository).save(any(User.class));
        verify(careerProfileService).getOrCreate(1L);
        assertThat(result).isNotNull();
        String email = result.getAttribute("email");
        assertThat(email).isEqualTo(ALLOWED_EMAIL);
    }

    @Test
    void shouldNotDuplicateIdentity_whenWhitelistedReturningLogin() {
        // Arrange — whitelisted email already provisioned (returning login).
        User existing = mock(User.class);
        lenient().when(existing.getId()).thenReturn(2L);
        lenient().when(delegate.loadUser(userRequest)).thenReturn(googlePrincipal(ALLOWED_EMAIL));
        lenient().when(userRepository.findByEmail(ALLOWED_EMAIL)).thenReturn(Optional.of(existing));

        // Act
        OAuth2User result = service().loadUser(userRequest);

        // Assert — no duplicate identity row is written for a returning user.
        verify(userRepository, never()).save(any());
        assertThat(result).isNotNull();
    }
}
