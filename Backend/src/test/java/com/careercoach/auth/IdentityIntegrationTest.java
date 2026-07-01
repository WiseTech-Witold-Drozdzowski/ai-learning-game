package com.careercoach.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.AvatarTier;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;

import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;

/**
 * End-to-end identity slice (BACKEND_DESIGN §2.1 / §8) against a real Postgres
 * (provided by {@code run-tests.sh}). Covers the JSONB round-trip of the typed
 * {@link AvatarState}, the secured {@code /api/me} path and provisioning of the
 * career profile on first access.
 *
 * <p>Dummy OAuth2 client registration is supplied so the context starts once
 * {@code SecurityConfig} switches to {@code oauth2Login} in the implementation
 * stage. Red phase: {@code MeController} still throws, so the secured-path
 * assertions fail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.auth.whitelist=author@example.com"
})
class IdentityIntegrationTest {

    private static final String EMAIL = "author@example.com";
    private static final String GOOGLE_SUB = "google-sub-123";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CareerProfileRepository careerProfileRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // FK order: profiles reference users.
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void avatarStateShouldRoundTripAsJsonb() {
        // Arrange — persist a profile carrying a typed avatar state.
        User user = userRepository.save(new User("roundtrip@example.com", "sub-roundtrip", "RoundTrip"));
        AvatarState avatar = new AvatarState(AvatarTier.GOLD, List.of("crown", "cape"));
        careerProfileRepository.save(new CareerProfile(user.getId(), 999L, 7, avatar));

        // Act — reload from the database (fresh transaction → genuine JSONB round-trip).
        CareerProfile reloaded = careerProfileRepository.findById(user.getId()).orElseThrow();

        // Assert
        assertThat(reloaded.getAvatarState()).isEqualTo(avatar);
        assertThat(reloaded.getAvatarState().tier()).isEqualTo(AvatarTier.GOLD);
        assertThat(reloaded.getAvatarState().unlockedAttributes()).containsExactly("crown", "cape");
        assertThat(reloaded.getTotalExp()).isEqualTo(999L);
        assertThat(reloaded.getLevel()).isEqualTo(7);
    }

    @Test
    void meShouldRequireAuthentication() throws Exception {
        // Act — unauthenticated request to the protected endpoint.
        MvcResult result = mockMvc.perform(get("/api/me")).andReturn();

        // Assert — 401 or a redirect to the login flow (never a 2xx).
        int httpStatus = result.getResponse().getStatus();
        assertThat(httpStatus)
                .withFailMessage("expected 401 or a login redirect but got %s", httpStatus)
                .isIn(302, 303, 401, 403);
    }

    @Test
    void meShouldReturnIdentityAndProvisionProfile_whenAuthenticated() throws Exception {
        // Arrange — identity exists (provisioned at login); profile not yet created.
        User user = userRepository.save(new User(EMAIL, GOOGLE_SUB, "Author"));

        // Act / Assert — authenticated request returns the identity.
        mockMvc.perform(get("/api/me").with(oauth2Login()
                        .attributes(attrs -> {
                            attrs.put("email", EMAIL);
                            attrs.put("sub", GOOGLE_SUB);
                        })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.profile").exists());

        // Assert — the career profile was provisioned on first access.
        assertThat(careerProfileRepository.findById(user.getId())).isPresent();
    }
}
