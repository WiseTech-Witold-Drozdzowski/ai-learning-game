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

import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.service.ConfigEntryNotFoundException;
import com.careercoach.config.service.SkillDefinitionService;
import tools.jackson.databind.ObjectMapper;

/**
 * Web-slice test for {@code /api/skill-defs} (issue-2) — controller + JSON
 * serialization / validation with the service mocked. Red phase: the
 * controller body throws {@code UnsupportedOperationException}.
 */
@WebMvcTest(SkillDefController.class)
class SkillDefControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SkillDefinitionService skillDefinitionService;

    @Test
    @WithMockUser
    void shouldReturnCatalog_whenListed() throws Exception {
        // Arrange
        SkillDefinition java = new SkillDefinition("JAVA", "Java", "TECHNICAL");
        when(skillDefinitionService.list()).thenReturn(List.of(java));

        // Act / Assert
        mockMvc.perform(get("/api/skill-defs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("JAVA"))
                .andExpect(jsonPath("$[0].displayName").value("Java"))
                .andExpect(jsonPath("$[0].category").value("TECHNICAL"));
    }

    @Test
    @WithMockUser
    void shouldReturnEntry_whenPresent() throws Exception {
        // Arrange
        SkillDefinition java = new SkillDefinition("JAVA", "Java", "TECHNICAL");
        when(skillDefinitionService.get("JAVA")).thenReturn(java);

        // Act / Assert
        mockMvc.perform(get("/api/skill-defs/JAVA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("JAVA"))
                .andExpect(jsonPath("$.category").value("TECHNICAL"));
    }

    @Test
    @WithMockUser
    void shouldReturnNotFound_whenEntryMissing() throws Exception {
        // Arrange
        when(skillDefinitionService.get("MISSING")).thenThrow(new ConfigEntryNotFoundException("MISSING"));

        // Act / Assert
        mockMvc.perform(get("/api/skill-defs/MISSING"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void shouldUpsertAndReturnEntry_whenBodyValid() throws Exception {
        // Arrange
        SkillDefinition saved = new SkillDefinition("NEW_SKILL", "New skill", "SOFT");
        when(skillDefinitionService.upsert(eq("NEW_SKILL"), any(SkillUpsertRequest.class))).thenReturn(saved);
        String body = objectMapper.writeValueAsString(new SkillUpsertRequest("New skill", "SOFT"));

        // Act / Assert
        mockMvc.perform(put("/api/skill-defs/NEW_SKILL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("NEW_SKILL"))
                .andExpect(jsonPath("$.displayName").value("New skill"))
                .andExpect(jsonPath("$.category").value("SOFT"));
    }

    @Test
    @WithMockUser
    void shouldRejectBlankDisplayName() throws Exception {
        // Arrange
        String body = objectMapper.writeValueAsString(new SkillUpsertRequest("", "SOFT"));

        // Act / Assert
        mockMvc.perform(put("/api/skill-defs/SOME_SKILL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void shouldRejectBlankCategory() throws Exception {
        // Arrange
        String body = objectMapper.writeValueAsString(new SkillUpsertRequest("Some skill", ""));

        // Act / Assert
        mockMvc.perform(put("/api/skill-defs/SOME_SKILL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
