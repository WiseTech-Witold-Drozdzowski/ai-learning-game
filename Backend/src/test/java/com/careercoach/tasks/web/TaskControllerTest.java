package com.careercoach.tasks.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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

import com.careercoach.auth.domain.User;
import com.careercoach.auth.service.CurrentUserService;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.tasks.domain.Quiz;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.domain.exception.ArtifactRequiredException;
import com.careercoach.tasks.domain.exception.IllegalTaskStateTransitionException;
import com.careercoach.tasks.domain.exception.TaskNotFoundException;
import com.careercoach.tasks.domain.exception.UnsupportedVerificationMethodException;
import com.careercoach.tasks.service.TaskService;
import com.careercoach.tasks.web.model.SubmitRequest;

import tools.jackson.databind.ObjectMapper;

/**
 * Web-slice test for {@code /api/tasks} (issue-6) — controller + JSON
 * serialization with the service mocked. Red phase: the controller body
 * throws {@code UnsupportedOperationException}.
 */
@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private CurrentUserService currentUserService;

    private static Task task(Long id, TaskState state) {
        return Task.builder()
                .id(id)
                .goalId(5L)
                .typeKey("HONOR_CHECK")
                .title("Do the thing")
                .description("desc")
                .state(state)
                .skillKeys(List.of("JAVA"))
                .expAwarded(state == TaskState.DONE ? 10L : 0L)
                .build();
    }

    private void stubCurrentUser(Long userId) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(userId);
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    @WithMockUser
    void start_shouldReturnInProgress() throws Exception {
        // Arrange
        when(taskService.start(1L)).thenReturn(task(1L, TaskState.IN_PROGRESS));

        // Act / Assert
        mockMvc.perform(post("/api/tasks/1/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("IN_PROGRESS"));
    }

    @Test
    @WithMockUser
    void start_shouldReturnConflict_whenIllegalTransition() throws Exception {
        // Arrange
        when(taskService.start(1L))
                .thenThrow(new IllegalTaskStateTransitionException(1L, "start", TaskState.DONE));

        // Act / Assert
        mockMvc.perform(post("/api/tasks/1/start"))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser
    void submit_shouldReturnDone_whenNoBody() throws Exception {
        // Arrange
        stubCurrentUser(42L);
        when(taskService.submit(eq(1L), eq(42L), isNull(), isNull())).thenReturn(task(1L, TaskState.DONE));

        // Act / Assert
        mockMvc.perform(post("/api/tasks/1/submit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DONE"));
        verify(taskService).submit(1L, 42L, null, null);
    }

    @Test
    @WithMockUser
    void submit_shouldPassArtifact_whenBodyProvided() throws Exception {
        // Arrange
        stubCurrentUser(42L);
        when(taskService.submit(eq(1L), eq(42L), eq("http://proof"), isNull())).thenReturn(task(1L, TaskState.DONE));
        String body = objectMapper.writeValueAsString(new SubmitRequest("http://proof", null));

        // Act / Assert
        mockMvc.perform(post("/api/tasks/1/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DONE"));
        verify(taskService).submit(1L, 42L, "http://proof", null);
    }

    @Test
    @WithMockUser
    void submit_shouldReturnBadRequest_whenArtifactRequired() throws Exception {
        // Arrange
        stubCurrentUser(42L);
        when(taskService.submit(eq(1L), eq(42L), isNull(), isNull()))
                .thenThrow(new ArtifactRequiredException(1L));

        // Act / Assert
        mockMvc.perform(post("/api/tasks/1/submit"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void submit_shouldReturnNotImplemented_whenVerificationMethodUnsupported() throws Exception {
        // Arrange
        stubCurrentUser(42L);
        when(taskService.submit(eq(1L), eq(42L), isNull(), isNull()))
                .thenThrow(new UnsupportedVerificationMethodException(VerificationMethod.AI_DIALOG));

        // Act / Assert
        mockMvc.perform(post("/api/tasks/1/submit"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    @WithMockUser
    void get_shouldReturnTask() throws Exception {
        // Arrange
        when(taskService.get(1L)).thenReturn(task(1L, TaskState.TODO));

        // Act / Assert
        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.state").value("TODO"))
                .andExpect(jsonPath("$.typeKey").value("HONOR_CHECK"));
    }

    @Test
    @WithMockUser
    void get_shouldNotLeakQuizAnswerKey() throws Exception {
        // Arrange — a task carrying a quiz with an answer key
        Task withQuiz = task(1L, TaskState.IN_PROGRESS);
        withQuiz.setQuiz(new Quiz(List.of(new Quiz.Question("Q1", List.of("A", "B"), "A"))));
        when(taskService.get(1L)).thenReturn(withQuiz);

        // Act / Assert — the whole quiz field is @JsonIgnore'd, so the answer key never ships
        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.quiz").doesNotExist());
    }

    @Test
    @WithMockUser
    void get_shouldReturnNotFound_whenMissing() throws Exception {
        // Arrange
        when(taskService.get(99L)).thenThrow(new TaskNotFoundException(99L));

        // Act / Assert
        mockMvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound());
    }
}
