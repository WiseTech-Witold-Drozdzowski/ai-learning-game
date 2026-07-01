package com.careercoach.goals;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;

import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end goals slice (issue-3) against a real Postgres (provided by
 * {@code run-tests.sh}). Covers creation, the accept/close state machine and
 * nested-tree reads through the real REST surface.
 *
 * <p>Dummy OAuth2 client registration is supplied so the context starts once
 * {@code SecurityConfig} switches to {@code oauth2Login} in the implementation
 * stage. Red phase: the controller/service still throw, so most assertions fail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class GoalTreeIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        goalRepository.deleteAll();

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void createAcceptCloseFlow_shouldPersistStates() throws Exception {
        // Arrange
        String body = """
                {"title":"Become senior engineer","description":"desc"}
                """;

        // Act — create.
        MvcResult createResult = mockMvc.perform(post("/api/goals").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("PROPOSED"))
                .andReturn();
        Long id = extractId(createResult);

        // Act — accept.
        mockMvc.perform(post("/api/goals/" + id + "/accept").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));

        // Act — close.
        mockMvc.perform(post("/api/goals/" + id + "/close").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"));

        // Assert — final persisted state.
        Goal reloaded = goalRepository.findById(id).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(GoalState.CLOSED);
    }

    @Test
    void getTree_shouldReturnNestedGoalsWithStates() throws Exception {
        // Arrange — seed a root and a LEVEL child directly through the repository.
        Goal root = goalRepository.save(Goal.builder()
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Root goal")
                .description("desc")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build());
        goalRepository.save(Goal.builder()
                .parentId(root.getId())
                .kind(GoalKind.LEVEL)
                .title("Child goal")
                .description("desc")
                .state(GoalState.PROPOSED)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0)
                .build());

        // Act / Assert
        mockMvc.perform(get("/api/goals").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Root goal"))
                .andExpect(jsonPath("$[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$[0].children[0].title").value("Child goal"))
                .andExpect(jsonPath("$[0].children[0].state").value("PROPOSED"));
    }

    @Test
    void createStrategicWithParentId_shouldReturn400_andWriteNoRow() throws Exception {
        // Arrange
        String body = """
                {"title":"Bad goal","description":"desc","parentId":42}
                """;

        // Act / Assert
        mockMvc.perform(post("/api/goals").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(goalRepository.count()).isZero();
    }

    @Test
    void closeFromProposed_shouldReturnError_andLeaveStateUnchanged() throws Exception {
        // Arrange — create a goal, left in PROPOSED.
        String body = """
                {"title":"Become senior engineer","description":"desc"}
                """;
        MvcResult createResult = mockMvc.perform(post("/api/goals").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        Long id = extractId(createResult);

        // Act / Assert — closing a PROPOSED goal is illegal.
        mockMvc.perform(post("/api/goals/" + id + "/close").with(oauth2Login()))
                .andExpect(status().isConflict());

        Goal reloaded = goalRepository.findById(id).orElseThrow();
        assertThat(reloaded.getState()).isEqualTo(GoalState.PROPOSED);
    }

    @Test
    void getGoals_shouldRequireAuthentication() throws Exception {
        // Act — unauthenticated request to the protected endpoint.
        MvcResult result = mockMvc.perform(get("/api/goals")).andReturn();

        // Assert — 401 or a redirect to the login flow (never a 2xx).
        int httpStatus = result.getResponse().getStatus();
        assertThat(httpStatus)
                .withFailMessage("expected 401 or a login redirect but got %s", httpStatus)
                .isIn(302, 303, 401, 403);
    }

    private Long extractId(MvcResult result) throws Exception {
        String json = result.getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asLong();
    }
}
