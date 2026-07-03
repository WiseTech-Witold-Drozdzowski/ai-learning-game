package com.careercoach.gamification.web;

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
import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.ProfileResponse;
import com.careercoach.gamification.web.model.SkillView;

/**
 * Web-slice test for {@code GET /api/profile} (issue-6) — controller + JSON
 * serialization with the query service mocked. Red phase: the controller body
 * throws {@code UnsupportedOperationException}.
 */
@WebMvcTest(ProfileController.class)
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileQueryService profileQueryService;

    @MockitoBean
    private CurrentUserService currentUserService;

    @Test
    @WithMockUser
    void profile_shouldReturnTotalsLevelAvatarAndSkills() throws Exception {
        // Arrange
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(currentUserService.getCurrentUser()).thenReturn(user);

        ProfileResponse response = new ProfileResponse(
                250L, 3, new AvatarState(AvatarTier.SILVER, List.of("starter")),
                List.of(new SkillView("JAVA", "Java", 3, 250L)));
        when(profileQueryService.getProfile(7L)).thenReturn(response);

        // Act / Assert
        mockMvc.perform(get("/api/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExp").value(250))
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.avatarState.tier").value("SILVER"))
                .andExpect(jsonPath("$.skills[0].key").value("JAVA"))
                .andExpect(jsonPath("$.skills[0].level").value(3))
                .andExpect(jsonPath("$.skills[0].exp").value(250));
    }
}
