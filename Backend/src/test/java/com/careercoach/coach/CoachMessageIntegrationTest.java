package com.careercoach.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import com.careercoach.ai.OpenRouterStreamListener;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalCreatedBy;
import com.careercoach.goals.domain.GoalKind;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.repository.GoalRepository;

/**
 * End-to-end strategic-chat slice (issue-3) against a real Postgres (provided by
 * {@code run-tests.sh}), with the AI port replaced by a Mockito stub that emits a fixed
 * sequence of fragments. Consumes the SSE response through REST and verifies fragments
 * arrive as separate frames in order (incremental, not one blob at the end), that the
 * prompt is assembler-grounded, and that a mid-stream port error is signalled on the SSE
 * stream. A synchronous executor makes the streaming deterministic. Red phase: the service
 * throws, so no stream is produced.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(CoachMessageIntegrationTest.SyncExecutorConfig.class)
class CoachMessageIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {

        @Bean(name = "coachChatExecutor")
        Executor coachChatExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private GoalRepository goalRepository;

    @MockitoBean
    private OpenRouterClient openRouterClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        goalRepository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    private Goal saveActiveGoal() {
        return goalRepository.save(Goal.builder()
                .parentId(null)
                .kind(GoalKind.STRATEGIC)
                .title("Become a staff engineer")
                .description("desc")
                .state(GoalState.ACTIVE)
                .createdBy(GoalCreatedBy.USER)
                .orderIndex(0)
                .expEarned(0L)
                .build());
    }

    private String body(Long goalId) {
        return "{\"goalId\":" + goalId + ",\"message\":\"what should I focus on?\",\"history\":[]}";
    }

    @Test
    void messages_shouldStreamFragmentsAsOrderedSseEvents() throws Exception {
        // Arrange — the port emits three fragments then completes
        Goal goal = saveActiveGoal();
        doAnswer(inv -> {
            OpenRouterStreamListener listener = inv.getArgument(1);
            listener.onToken("Focus ");
            listener.onToken("on ");
            listener.onToken("systems design.");
            listener.onComplete();
            return null;
        }).when(openRouterClient).stream(anyString(), any());

        // Act — start the async request, then dispatch the completed async result
        MvcResult result = mockMvc.perform(post("/api/coach/messages").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(goal.getId())))
                .andReturn();
        String responseBody = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Assert — three separate token frames, delivered in the order they streamed in
        assertThat(responseBody).contains("Focus ").contains("on ").contains("systems design.");
        assertThat(responseBody.indexOf("Focus "))
                .isLessThan(responseBody.indexOf("on "));
        assertThat(responseBody.indexOf("on "))
                .isLessThan(responseBody.indexOf("systems design."));
        // Framed as distinct SSE token events, not one buffered blob.
        assertThat(responseBody.split("event:token", -1)).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void messages_shouldBuildPromptFromAssembledContext() throws Exception {
        // Arrange
        Goal goal = saveActiveGoal();
        doAnswer(inv -> {
            OpenRouterStreamListener listener = inv.getArgument(1);
            listener.onToken("ok");
            listener.onComplete();
            return null;
        }).when(openRouterClient).stream(anyString(), any());

        // Act
        MvcResult result = mockMvc.perform(post("/api/coach/messages").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(goal.getId())))
                .andReturn();
        mockMvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        // Assert — the prompt is grounded in real DB state (the goal title), not generic
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(openRouterClient).stream(promptCaptor.capture(), any());
        assertThat(promptCaptor.getValue())
                .contains("Become a staff engineer")
                .contains("what should I focus on?");
    }

    @Test
    void messages_shouldSignalErrorOnSse_whenPortFailsMidStream() throws Exception {
        // Arrange — one fragment, then a mid-stream failure
        Goal goal = saveActiveGoal();
        doAnswer(inv -> {
            OpenRouterStreamListener listener = inv.getArgument(1);
            listener.onToken("Star");
            listener.onError(new RuntimeException("upstream exploded"));
            return null;
        }).when(openRouterClient).stream(anyString(), any());

        // Act
        MvcResult result = mockMvc.perform(post("/api/coach/messages").with(oauth2Login())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(goal.getId())))
                .andReturn();
        String responseBody = mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Assert — the token arrived, then the failure is surfaced as an SSE error event
        assertThat(responseBody).contains("Star");
        assertThat(responseBody).contains("event:error");
        assertThat(responseBody.indexOf("Star")).isLessThan(responseBody.indexOf("event:error"));
    }
}
