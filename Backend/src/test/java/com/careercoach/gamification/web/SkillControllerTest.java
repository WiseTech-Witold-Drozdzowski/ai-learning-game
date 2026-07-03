package com.careercoach.gamification.web;

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

import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.SkillView;

/**
 * Web-slice test for {@code GET /api/skills} (issue-6) — controller + JSON
 * serialization with the query service mocked. Red phase: the controller body
 * throws {@code UnsupportedOperationException}.
 */
@WebMvcTest(SkillController.class)
class SkillControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileQueryService profileQueryService;

    @Test
    @WithMockUser
    void skills_shouldReturnKeyDisplayNameLevelAndExp() throws Exception {
        // Arrange
        when(profileQueryService.listSkills()).thenReturn(List.of(
                new SkillView("JAVA", "Java", 2, 120L),
                new SkillView("SPRING", "Spring", 1, 30L)));

        // Act / Assert
        mockMvc.perform(get("/api/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("JAVA"))
                .andExpect(jsonPath("$[0].displayName").value("Java"))
                .andExpect(jsonPath("$[0].level").value(2))
                .andExpect(jsonPath("$[0].exp").value(120))
                .andExpect(jsonPath("$[1].key").value("SPRING"));
    }
}
