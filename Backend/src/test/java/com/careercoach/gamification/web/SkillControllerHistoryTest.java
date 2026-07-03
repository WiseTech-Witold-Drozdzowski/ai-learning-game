package com.careercoach.gamification.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careercoach.gamification.service.ProfileQueryService;
import com.careercoach.gamification.web.model.ExpEventView;

/**
 * Web-slice test for {@code GET /api/skills/{key}/history} (issue-7) — controller + JSON
 * serialization with the query service mocked. Red phase: the controller body
 * throws {@code NotImplementedException}.
 */
@WebMvcTest(SkillController.class)
class SkillControllerHistoryTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProfileQueryService profileQueryService;

    @Test
    @WithMockUser
    void history_shouldReturnChronologicalLedgerFields_forSkill() throws Exception {
        // Arrange
        Instant now = Instant.now();
        when(profileQueryService.listSkillHistory("JAVA")).thenReturn(List.of(
                new ExpEventView(100L, 1L, 30L, "task-complete", now),
                new ExpEventView(101L, 2L, 20L, "task-complete", now.plusSeconds(1))));

        // Act / Assert
        mockMvc.perform(get("/api/skills/JAVA/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceTaskId").value(100))
                .andExpect(jsonPath("$[0].attemptId").value(1))
                .andExpect(jsonPath("$[0].amount").value(30))
                .andExpect(jsonPath("$[0].reason").value("task-complete"))
                .andExpect(jsonPath("$[1].sourceTaskId").value(101))
                .andExpect(jsonPath("$[1].attemptId").value(2))
                .andExpect(jsonPath("$[1].amount").value(20));
    }

    @Test
    @WithMockUser
    void history_shouldReturnEmptyArray_whenNoEvents() throws Exception {
        // Arrange
        when(profileQueryService.listSkillHistory("JAVA")).thenReturn(List.of());

        // Act / Assert
        mockMvc.perform(get("/api/skills/JAVA/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser
    void history_shouldSerializeNullAttemptId_whenAbsent() throws Exception {
        // Arrange
        Instant now = Instant.now();
        when(profileQueryService.listSkillHistory("JAVA")).thenReturn(List.of(
                new ExpEventView(100L, null, 30L, "task-complete", now)));

        // Act / Assert
        mockMvc.perform(get("/api/skills/JAVA/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceTaskId").value(100))
                .andExpect(jsonPath("$[0].attemptId").isEmpty())
                .andExpect(jsonPath("$[0].amount").value(30));
    }
}
