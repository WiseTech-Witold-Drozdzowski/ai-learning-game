package com.careercoach.auth.web;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careercoach.auth.domain.User;
import com.careercoach.auth.service.CurrentUserService;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.AvatarTier;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.service.CareerProfileService;

/**
 * Web-slice test for {@code GET /api/me} (BACKEND_DESIGN §7) — controller +
 * JSON serialization with the collaborators mocked. Red phase: the controller
 * body throws {@code UnsupportedOperationException}.
 */
@WebMvcTest(MeController.class)
class MeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @MockitoBean
    private CareerProfileService careerProfileService;

    @Test
    @WithMockUser
    void meShouldReturnIdentityAndProfile_whenAuthenticated() throws Exception {
        // Arrange — current user + their profile roll-up.
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getEmail()).thenReturn("author@example.com");
        when(user.getDisplayName()).thenReturn("Author");
        when(currentUserService.getCurrentUser()).thenReturn(user);

        CareerProfile profile = new CareerProfile(7L, 250L, 4, new AvatarState(AvatarTier.SILVER, List.of("starter")));
        // The controller may provision-on-read (getOrCreate) or read an existing profile.
        when(careerProfileService.getOrCreate(7L)).thenReturn(profile);
        when(careerProfileService.getForUser(7L)).thenReturn(profile);

        // Act / Assert
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.email").value("author@example.com"))
                .andExpect(jsonPath("$.displayName").value("Author"))
                .andExpect(jsonPath("$.profile.totalExp").value(250))
                .andExpect(jsonPath("$.profile.level").value(4))
                .andExpect(jsonPath("$.profile.avatarState.tier").value("SILVER"));
    }
}
