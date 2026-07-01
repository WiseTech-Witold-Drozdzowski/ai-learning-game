package com.careercoach.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.web.context.WebApplicationContext;

import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.config.repository.TaskTypeDefinitionRepository;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * End-to-end config-catalog slice (issue-2) against a real Postgres (provided
 * by {@code run-tests.sh}). Covers startup seeding, catalog reads and runtime
 * PUT edits, all without a restart.
 *
 * <p>Dummy OAuth2 client registration is supplied so the context starts once
 * {@code SecurityConfig} switches to {@code oauth2Login} in the implementation
 * stage. Red phase: the controllers still throw, so most assertions fail.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
})
class ConfigCatalogIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TaskTypeDefinitionRepository taskTypeDefinitionRepository;

    @Autowired
    private SkillDefinitionRepository skillDefinitionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void seededCatalogsShouldBeNonEmpty_afterStartup() {
        // Assert — ApplicationReadyEvent has already run the seeder by the time the context is up.
        assertThat(taskTypeDefinitionRepository.count()).isGreaterThan(0);
        assertThat(skillDefinitionRepository.count()).isGreaterThan(0);
        assertThat(taskTypeDefinitionRepository.findById("HONOR_CHECK")).isPresent();
        assertThat(skillDefinitionRepository.findById("JAVA")).isPresent();
    }

    @Test
    void shouldReturnSeededCatalogs_whenListed() throws Exception {
        mockMvc.perform(get("/api/task-types").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.key=='HONOR_CHECK')]").exists());

        mockMvc.perform(get("/api/skill-defs").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.key=='JAVA')]").exists());
    }

    @Test
    void shouldReturnSeededEntry_whenFetchedByKey() throws Exception {
        mockMvc.perform(get("/api/task-types/HONOR_CHECK").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("HONOR_CHECK"));

        mockMvc.perform(get("/api/skill-defs/JAVA").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("JAVA"));
    }

    @Test
    void putShouldCreateTaskType_visibleImmediately() throws Exception {
        // Arrange
        String body = """
                {"displayName":"IT new type","verificationMethod":"HONOR","expBase":5,
                 "expScaleByScore":false,"requiresArtifact":false}
                """;

        // Act
        mockMvc.perform(put("/api/task-types/IT_NEW_TASK_TYPE").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("IT_NEW_TASK_TYPE"));

        // Assert — immediately visible without a restart.
        mockMvc.perform(get("/api/task-types/IT_NEW_TASK_TYPE").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("IT new type"))
                .andExpect(jsonPath("$.expBase").value(5));
    }

    @Test
    void putShouldUpdateExistingTaskType_visibleImmediately() throws Exception {
        // Arrange
        String body = """
                {"displayName":"Honor check (updated)","verificationMethod":"HONOR","expBase":42,
                 "expScaleByScore":true,"requiresArtifact":false}
                """;

        // Act
        mockMvc.perform(put("/api/task-types/HONOR_CHECK").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expBase").value(42));

        // Assert — immediately visible without a restart.
        mockMvc.perform(get("/api/task-types/HONOR_CHECK").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Honor check (updated)"))
                .andExpect(jsonPath("$.expBase").value(42));
    }

    @Test
    void putShouldCreateSkillDef_visibleImmediately() throws Exception {
        // Arrange
        String body = """
                {"displayName":"IT new skill","category":"SOFT"}
                """;

        // Act
        mockMvc.perform(put("/api/skill-defs/IT_NEW_SKILL").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("IT_NEW_SKILL"));

        // Assert — immediately visible without a restart.
        mockMvc.perform(get("/api/skill-defs/IT_NEW_SKILL").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("IT new skill"))
                .andExpect(jsonPath("$.category").value("SOFT"));
    }

    @Test
    void putShouldRejectInvalidVerificationMethod_andWriteNoRow() throws Exception {
        // Arrange
        String body = """
                {"displayName":"Bad type","verificationMethod":"NOT_A_METHOD","expBase":10,
                 "expScaleByScore":false,"requiresArtifact":false}
                """;

        // Act / Assert
        mockMvc.perform(put("/api/task-types/IT_BAD_VERIFICATION").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(taskTypeDefinitionRepository.findById("IT_BAD_VERIFICATION")).isEmpty();
    }

    @Test
    void putShouldRejectNegativeExpBase_andWriteNoRow() throws Exception {
        // Arrange
        String body = """
                {"displayName":"Bad type","verificationMethod":"HONOR","expBase":-1,
                 "expScaleByScore":false,"requiresArtifact":false}
                """;

        // Act / Assert
        mockMvc.perform(put("/api/task-types/IT_NEGATIVE_EXP").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(taskTypeDefinitionRepository.findById("IT_NEGATIVE_EXP")).isEmpty();
    }
}
