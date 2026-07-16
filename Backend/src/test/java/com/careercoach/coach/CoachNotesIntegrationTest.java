package com.careercoach.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.ai.OpenRouterStreamListener;
import com.careercoach.auth.domain.User;
import com.careercoach.auth.repository.UserRepository;
import com.careercoach.coach.domain.CoachNote;
import com.careercoach.coach.repository.CoachNoteRepository;
import com.careercoach.coach.repository.MockMessageRepository;
import com.careercoach.coach.repository.MockSessionRepository;
import com.careercoach.coach.service.ContextAssembler;
import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.config.web.TaskTypeUpsertRequest;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.repository.CareerProfileRepository;
import com.careercoach.gamification.repository.ExpEventRepository;
import com.careercoach.gamification.repository.SkillRepository;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;
import com.careercoach.jobs.JobRepository;
import com.careercoach.jobs.JobRunner;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.repository.TaskRepository;

/**
 * End-to-end coach-memory slice (issue-7) against a real Postgres (provided by
 * {@code run-tests.sh}) with the AI port replaced by a Mockito stub. Covers the
 * transparency surface — {@code GET/PUT/DELETE /api/coach-notes} — the assembler
 * integration (active notes enter the prompt, inactive ones do not), and that a mock
 * session contributes only a distillate to {@code coach_notes}, never the raw transcript.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(CoachNotesIntegrationTest.SyncExecutorConfig.class)
class CoachNotesIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean(name = "coachChatExecutor")
        Executor coachChatExecutor() {
            return Runnable::run;
        }

        @Bean(name = "jobExecutor")
        Executor jobExecutor() {
            return Runnable::run;
        }
    }

    @Autowired private WebApplicationContext context;
    @Autowired private CoachNoteRepository coachNoteRepository;
    @Autowired private ContextAssembler contextAssembler;
    @Autowired private JobRunner jobRunner;
    @Autowired private JobRepository jobRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private GoalRepository goalRepository;
    @Autowired private MockSessionRepository mockSessionRepository;
    @Autowired private MockMessageRepository mockMessageRepository;
    @Autowired private ExpEventRepository expEventRepository;
    @Autowired private SkillRepository skillRepository;
    @Autowired private SkillDefinitionRepository skillDefinitionRepository;
    @Autowired private CareerProfileRepository careerProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskTypeDefinitionService taskTypeDefinitionService;

    @MockitoBean private OpenRouterClient openRouterClient;

    private MockMvc mockMvc;
    private User user;
    private Goal goal;

    @BeforeEach
    void setUp() {
        coachNoteRepository.deleteAll();
        mockMessageRepository.deleteAll();
        mockSessionRepository.deleteAll();
        jobRepository.deleteAll();
        expEventRepository.deleteAll();
        skillRepository.deleteAll();
        taskRepository.deleteAll();
        goalRepository.deleteAll();
        careerProfileRepository.deleteAll();
        userRepository.deleteAll();

        if (!skillDefinitionRepository.existsById("COMMUNICATION")) {
            skillDefinitionRepository.save(SkillDefinition.builder()
                    .key("COMMUNICATION").displayName("Communication").category("SOFT").build());
        }
        taskTypeDefinitionService.upsert("MOCK",
                new TaskTypeUpsertRequest("Mock interview", VerificationMethod.AI_DIALOG, 60, false, false));

        user = userRepository.save(new User("grad@example.com", "sub-grad", "Grad"));
        careerProfileRepository.save(new CareerProfile(user.getId(), 0L, 1, AvatarState.initial()));
        goal = goalRepository.save(Goal.builder()
                .parentId(null).kind(GoalKind.STRATEGIC).title("Behavioural preparation").description("desc")
                .state(GoalState.ACTIVE).createdBy(GoalCreatedBy.USER).orderIndex(0).expEarned(0L)
                .build());

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private CoachNote saveNote(String content, boolean active) {
        return coachNoteRepository.save(CoachNote.builder().content(content).active(active).build());
    }

    // --- Transparency: GET / PUT / DELETE -------------------------------------------------

    @Test
    void list_shouldReturnAllNotes_includingInactive() throws Exception {
        // Arrange
        saveNote("active observation", true);
        saveNote("archived observation", false);

        // Act / Assert — the user sees every note, regardless of active flag
        mockMvc.perform(get("/api/coach-notes").with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.content=='active observation')]").exists())
                .andExpect(jsonPath("$[?(@.content=='archived observation')]").exists());
    }

    @Test
    void get_shouldReturnNote_whenExists_and404_whenMissing() throws Exception {
        // Arrange
        CoachNote note = saveNote("single note", true);

        // Act / Assert
        mockMvc.perform(get("/api/coach-notes/" + note.getId()).with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("single note"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(get("/api/coach-notes/999999").with(oauth2Login()))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_shouldEditContentAndActive_visibleImmediately() throws Exception {
        // Arrange
        CoachNote note = saveNote("before edit", true);

        // Act — the user rewrites the note and deactivates it
        mockMvc.perform(put("/api/coach-notes/" + note.getId()).with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"after edit\",\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("after edit"))
                .andExpect(jsonPath("$.active").value(false));

        // Assert — persisted, visible without a restart
        CoachNote reloaded = coachNoteRepository.findById(note.getId()).orElseThrow();
        assertThat(reloaded.getContent()).isEqualTo("after edit");
        assertThat(reloaded.isActive()).isFalse();
    }

    @Test
    void put_shouldReturn404_whenMissing() throws Exception {
        mockMvc.perform(put("/api/coach-notes/999999").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"x\",\"active\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void put_shouldReject_whenContentBlank() throws Exception {
        // Arrange
        CoachNote note = saveNote("keep me", true);

        // Act / Assert — validation rejects a blank note, nothing changes
        mockMvc.perform(put("/api/coach-notes/" + note.getId()).with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \",\"active\":true}"))
                .andExpect(status().isBadRequest());
        assertThat(coachNoteRepository.findById(note.getId()).orElseThrow().getContent()).isEqualTo("keep me");
    }

    @Test
    void delete_shouldRemoveNote_and404_whenMissing() throws Exception {
        // Arrange
        CoachNote note = saveNote("to delete", true);

        // Act / Assert
        mockMvc.perform(delete("/api/coach-notes/" + note.getId()).with(oauth2Login()))
                .andExpect(status().isNoContent());
        assertThat(coachNoteRepository.findById(note.getId())).isEmpty();

        mockMvc.perform(delete("/api/coach-notes/999999").with(oauth2Login()))
                .andExpect(status().isNotFound());
    }

    // --- Assembler integration ------------------------------------------------------------

    @Test
    void assembler_shouldIncludeActiveNotes_andExcludeInactiveOnes() {
        // Arrange — one active, one deactivated note
        saveNote("ACTIVE_NOTE_MARKER prefers hands-on", true);
        saveNote("INACTIVE_NOTE_MARKER old preference", false);

        // Act — assemble the coach context for the goal
        String prompt = contextAssembler.assemble(goal.getId());

        // Assert — active note enters the prompt (seam filled), inactive one does not
        assertThat(prompt).contains("ACTIVE_NOTE_MARKER");
        assertThat(prompt).doesNotContain("INACTIVE_NOTE_MARKER");
    }

    // --- Mock distillate, never the raw transcript ----------------------------------------

    @Test
    void mockEvaluation_shouldStoreDistillate_notRawTranscript() throws Exception {
        // Arrange — a mock task + a completed exchange whose transcript carries a marker
        Task task = taskRepository.save(Task.builder()
                .goalId(goal.getId()).typeKey("MOCK").title("Behavioural mock interview").description("desc")
                .state(TaskState.IN_PROGRESS).skillKeys(List.of("COMMUNICATION")).expAwarded(0L)
                .build());

        stubStream("RAW_TRANSCRIPT_MARKER opening question.");
        Long sessionId = start(task.getId());
        stubStream("RAW_TRANSCRIPT_MARKER coach follow-up.");
        respond(sessionId, "RAW_TRANSCRIPT_MARKER my answer.");

        // The grader returns a score plus an autonomous distillate note — a summary, not the transcript
        stubGrade("{\"score\":88,\"passed\":true,\"feedback\":\"Strong\","
                + "\"skills\":[{\"skillKey\":\"COMMUNICATION\",\"exp\":40}],"
                + "\"coachNotes\":[{\"action\":\"CREATE\",\"content\":\"Communicates clearly under pressure\"}]}");

        // Act — finish the session and run the EVALUATION job
        mockMvc.perform(post("/api/mock/" + sessionId + "/finish").with(oauth2Login()))
                .andExpect(status().isOk());
        jobRunner.pollOnce();

        // Assert — exactly the distillate was stored, and none of the raw transcript
        List<CoachNote> notes = coachNoteRepository.findAll();
        assertThat(notes).singleElement().satisfies(n -> {
            assertThat(n.getContent()).isEqualTo("Communicates clearly under pressure");
            assertThat(n.isActive()).isTrue();
        });
        assertThat(notes).noneSatisfy(n ->
                assertThat(n.getContent()).contains("RAW_TRANSCRIPT_MARKER"));
    }

    // --- helpers (mock streaming flow, mirrors MockInterviewIntegrationTest) ---------------

    private void stubStream(String... fragments) {
        doAnswer(inv -> {
            OpenRouterStreamListener listener = inv.getArgument(1);
            for (String fragment : fragments) {
                listener.onToken(fragment);
            }
            listener.onComplete();
            return null;
        }).when(openRouterClient).stream(anyString(), any());
    }

    private void stubGrade(String json) {
        when(openRouterClient.complete(anyString())).thenReturn(new OpenRouterCompletion(json));
    }

    private Long start(Long taskId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/tasks/" + taskId + "/mock/start").with(oauth2Login()))
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
        List<com.careercoach.coach.domain.MockSession> sessions = mockSessionRepository.findAll();
        assertThat(sessions).hasSize(1);
        return sessions.get(0).getId();
    }

    private void respond(Long sessionId, String message) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/mock/" + sessionId + "/messages").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());
    }
}
