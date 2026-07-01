package com.careercoach.config.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.ConfigEntryNotFoundException;
import com.careercoach.config.service.TaskTypeDefinitionService;
import tools.jackson.databind.ObjectMapper;

/**
 * Web-slice test for {@code /api/task-types} (issue-2) — controller + JSON
 * serialization / validation with the service mocked. Red phase: the
 * controller body throws {@code UnsupportedOperationException}.
 */
@WebMvcTest(TaskTypeController.class)
class TaskTypeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskTypeDefinitionService taskTypeDefinitionService;

    @Test
    @WithMockUser
    void shouldReturnCatalog_whenListed() throws Exception {
        // Arrange
        TaskTypeDefinition honorCheck = new TaskTypeDefinition("HONOR_CHECK", "Honor check",
                VerificationMethod.HONOR, 10, false, false);
        when(taskTypeDefinitionService.list()).thenReturn(List.of(honorCheck));

        // Act / Assert
        mockMvc.perform(get("/api/task-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("HONOR_CHECK"))
                .andExpect(jsonPath("$[0].displayName").value("Honor check"))
                .andExpect(jsonPath("$[0].verificationMethod").value("HONOR"));
    }

    @Test
    @WithMockUser
    void shouldReturnEntry_whenPresent() throws Exception {
        // Arrange
        TaskTypeDefinition honorCheck = new TaskTypeDefinition("HONOR_CHECK", "Honor check",
                VerificationMethod.HONOR, 10, false, false);
        when(taskTypeDefinitionService.get("HONOR_CHECK")).thenReturn(honorCheck);

        // Act / Assert
        mockMvc.perform(get("/api/task-types/HONOR_CHECK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("HONOR_CHECK"))
                .andExpect(jsonPath("$.expBase").value(10));
    }

    @Test
    @WithMockUser
    void shouldReturnNotFound_whenEntryMissing() throws Exception {
        // Arrange
        when(taskTypeDefinitionService.get("MISSING")).thenThrow(new ConfigEntryNotFoundException("MISSING"));

        // Act / Assert
        mockMvc.perform(get("/api/task-types/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void shouldUpsertAndReturnEntry_whenBodyValid() throws Exception {
        // Arrange
        TaskTypeDefinition saved = new TaskTypeDefinition("NEW_TYPE", "New type",
                VerificationMethod.HONOR_WITH_PROOF, 15, true, true);
        when(taskTypeDefinitionService.upsert(eq("NEW_TYPE"), any(TaskTypeUpsertRequest.class))).thenReturn(saved);
        String body = objectMapper.writeValueAsString(
                new TaskTypeUpsertRequest("New type", VerificationMethod.HONOR_WITH_PROOF, 15, true, true));

        // Act / Assert
        mockMvc.perform(put("/api/task-types/NEW_TYPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("NEW_TYPE"))
                .andExpect(jsonPath("$.displayName").value("New type"))
                .andExpect(jsonPath("$.verificationMethod").value("HONOR_WITH_PROOF"));
    }

    @Test
    @WithMockUser
    void shouldRejectUnknownVerificationMethod() throws Exception {
        // Arrange — raw JSON with a verificationMethod value outside the enum.
        String body = """
                {"displayName":"Bad type","verificationMethod":"NOT_A_METHOD","expBase":10,
                 "expScaleByScore":false,"requiresArtifact":false}
                """;

        // Act / Assert
        mockMvc.perform(put("/api/task-types/BAD_TYPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldRejectNegativeExpBase() throws Exception {
        // Arrange
        String body = objectMapper.writeValueAsString(
                new TaskTypeUpsertRequest("Some type", VerificationMethod.HONOR, -1, false, false));

        // Act / Assert
        mockMvc.perform(put("/api/task-types/SOME_TYPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldRejectBlankDisplayName() throws Exception {
        // Arrange
        String body = objectMapper.writeValueAsString(
                new TaskTypeUpsertRequest("", VerificationMethod.HONOR, 10, false, false));

        // Act / Assert
        mockMvc.perform(put("/api/task-types/SOME_TYPE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
