package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end job lifecycle (issue-1) against a real Postgres (provided by
 * {@code run-tests.sh}): enqueue → deterministic {@code pollOnce()} → DONE,
 * JSONB round-trip of typed payloads, the REST status read, and an SSE smoke
 * test — all through the real Spring context.
 *
 * <p>The scheduler is disabled and {@code pollOnce()} is triggered explicitly
 * (deterministic-poller convention); the {@code jobExecutor} bean is swapped
 * for a synchronous one so dispatched execution completes inline.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "careercoach.jobs.scheduler-enabled=false",
        "spring.main.allow-bean-definition-overriding=true"
})
@Import(JobLifecycleIntegrationTest.SyncExecutorConfig.class)
class JobLifecycleIntegrationTest {

    @TestConfiguration
    static class SyncExecutorConfig {

        @Bean(name = "jobExecutor")
        Executor jobExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    private JobRunner jobRunner;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jobRepository.deleteAll();

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void lifecycle_shouldReachDone_andRoundTripTypedPayload_whenPolled() {
        // Arrange
        Job enqueued = jobService.enqueue(JobType.ECHO, new EchoPayload("hello"), null, null);

        // Act
        jobRunner.pollOnce();

        // Assert
        Job reloaded = jobRepository.findById(enqueued.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(reloaded.getInput()).isEqualTo(new EchoPayload("hello"));
        assertThat(reloaded.getOutput()).isEqualTo(new EchoPayload("echo: hello"));
    }

    @Test
    void getJob_shouldReturnCurrentStatus_afterProcessing() throws Exception {
        // Arrange
        Job enqueued = jobService.enqueue(JobType.ECHO, new EchoPayload("hello"), null, null);
        jobRunner.pollOnce();

        // Act / Assert
        mockMvc.perform(get("/api/jobs/" + enqueued.getId()).with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"));
    }

    @Test
    void pollOnce_shouldClaimLeftoverQueuedJob_afterSimulatedRestart() {
        // Arrange — a QUEUED job already sitting in the DB, as if left over from before a restart.
        Job leftover = jobRepository.save(Job.queued(JobType.ECHO, new EchoPayload("leftover"), null, null, 3));

        // Act — a fresh poll cycle (simulating a restarted context) picks it up.
        jobRunner.pollOnce();

        // Assert
        Job reloaded = jobRepository.findById(leftover.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(JobStatus.DONE);
    }

    @Test
    void events_shouldReturnSseStream() throws Exception {
        // Act / Assert
        MvcResult result = mockMvc.perform(get("/api/events").with(oauth2Login()))
                .andExpect(request().asyncStarted())
                .andReturn();

        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
    }

    @Test
    void inputAndOutput_shouldBePersistedAsJsonb_andReloadable() {
        // Arrange
        Job enqueued = jobService.enqueue(JobType.ECHO, new EchoPayload("round-trip"), null, null);

        // Act
        jobRunner.pollOnce();
        Job reloaded = jobRepository.findById(enqueued.getId()).orElseThrow();

        // Assert
        assertThat(reloaded.getInput()).isEqualTo(new EchoPayload("round-trip"));
        assertThat(reloaded.getOutput()).isEqualTo(new EchoPayload("echo: round-trip"));
    }
}
