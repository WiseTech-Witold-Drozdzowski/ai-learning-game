package com.careercoach.goals.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.domain.exception.GoalInvariantViolationException;
import com.careercoach.goals.domain.exception.GoalNotFoundException;
import com.careercoach.goals.domain.exception.IllegalGoalStateTransitionException;
import com.careercoach.goals.service.GoalService;
import com.careercoach.goals.web.model.GoalCreateRequest;
import com.careercoach.goals.web.model.GoalNode;

import tools.jackson.databind.ObjectMapper;

/**
 * Web-slice test for {@code /api/goals} (issue-3) — controller + JSON
 * serialization / validation with the service mocked. Red phase: the
 * controller body throws {@code UnsupportedOperationException}.
 */
@WebMvcTest(GoalController.class)
class GoalControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoalService goalService;

    private static Goal goal(Long id, GoalKind kind, GoalState state) {
        return Goal.builder()
                .id(id)
                .parentId(null)
                .kind(kind)
                .title("Some goal")
                .description("desc")
                .state(state)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build();
    }

    @Test
    @WithMockUser
    void shouldCreateStrategicGoal_whenBodyValid() throws Exception {
        // Arrange
        when(goalService.createStrategic(any(GoalCreateRequest.class)))
                .thenReturn(goal(1L, GoalKind.STRATEGIC, GoalState.PROPOSED));
        String body = objectMapper.writeValueAsString(new GoalCreateRequest("Become senior engineer", "desc", null));

        // Act / Assert
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PROPOSED"))
                .andExpect(jsonPath("$.kind").value("STRATEGIC"));
    }

    @Test
    @WithMockUser
    void shouldRejectBlankTitle() throws Exception {
        // Arrange
        String body = objectMapper.writeValueAsString(new GoalCreateRequest("", "desc", null));

        // Act / Assert
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldReturnBadRequest_whenServiceThrowsInvariantViolation() throws Exception {
        // Arrange
        when(goalService.createStrategic(any(GoalCreateRequest.class)))
                .thenThrow(new GoalInvariantViolationException("STRATEGIC must not have a parent"));
        String body = objectMapper.writeValueAsString(new GoalCreateRequest("Bad goal", "desc", 42L));

        // Act / Assert
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldReturnNestedTree_whenListed() throws Exception {
        // Arrange
        GoalNode child = new GoalNode(2L, 1L, GoalKind.LEVEL, "Child goal", "desc",
                GoalState.PROPOSED, GoalCreatedBy.USER, 0, 0, List.of());
        GoalNode root = new GoalNode(1L, null, GoalKind.STRATEGIC, "Root goal", "desc",
                GoalState.PROPOSED, GoalCreatedBy.USER, 0, 0, List.of(child));
        when(goalService.getTree()).thenReturn(List.of(root));

        // Act / Assert
        mockMvc.perform(get("/api/goals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Root goal"))
                .andExpect(jsonPath("$[0].children[0].title").value("Child goal"));
    }

    @Test
    @WithMockUser
    void shouldAcceptGoal() throws Exception {
        // Arrange
        when(goalService.accept(eq(1L))).thenReturn(goal(1L, GoalKind.STRATEGIC, GoalState.ACTIVE));

        // Act / Assert
        mockMvc.perform(post("/api/goals/1/accept"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    @Test
    @WithMockUser
    void shouldReturnConflict_whenAcceptIllegal() throws Exception {
        // Arrange
        when(goalService.accept(eq(1L))).thenThrow(new IllegalGoalStateTransitionException("illegal"));

        // Act / Assert
        mockMvc.perform(post("/api/goals/1/accept"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void shouldReturnNotFound_whenAcceptingMissing() throws Exception {
        // Arrange
        when(goalService.accept(eq(99L))).thenThrow(new GoalNotFoundException("99"));

        // Act / Assert
        mockMvc.perform(post("/api/goals/99/accept"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void shouldCloseGoal() throws Exception {
        // Arrange
        when(goalService.close(eq(1L))).thenReturn(goal(1L, GoalKind.STRATEGIC, GoalState.CLOSED));

        // Act / Assert
        mockMvc.perform(post("/api/goals/1/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"));
    }

    @Test
    @WithMockUser
    void shouldReturnConflict_whenCloseIllegal() throws Exception {
        // Arrange
        when(goalService.close(eq(1L))).thenThrow(new IllegalGoalStateTransitionException("illegal"));

        // Act / Assert
        mockMvc.perform(post("/api/goals/1/close"))
                .andExpect(status().isConflict());
    }
}
