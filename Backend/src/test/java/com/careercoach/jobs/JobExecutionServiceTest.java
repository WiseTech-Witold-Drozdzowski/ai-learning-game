package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JobExecutionService} (issue-1) — single-job execution,
 * terminal/retry state transitions and SSE emission. Collaborators are mocked;
 * {@link Clock} is fixed for deterministic backoff assertions.
 */
@ExtendWith(MockitoExtension.class)
class JobExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobHandlerRegistry registry;

    @Mock
    private SseHub sseHub;

    private JobRunnerProperties props;
    private Clock clock;
    private JobExecutionService service;

    @BeforeEach
    void setUp() {
        props = new JobRunnerProperties();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new JobExecutionService(jobRepository, registry, sseHub, props, clock);
    }

    private static Job runningJob(int attempts, int maxAttempts) {
        Job job = new Job(JobType.ECHO, JobStatus.RUNNING);
        job.setInput(new EchoPayload("hi"));
        job.setAttempts(attempts);
        job.setMaxAttempts(maxAttempts);
        return job;
    }

    @Test
    void execute_shouldMarkDone_andEmitSse_whenHandlerSucceeds() {
        // Arrange
        Job job = runningJob(0, 3);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        JobResult result = new JobResult(new EchoPayload("echo: hi"));
        when(registry.dispatch(job)).thenReturn(result);
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.execute(1L);

        // Assert
        assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
        assertThat(job.getOutput()).isEqualTo(result.output());
        assertThat(job.getFinishedAt()).isEqualTo(NOW);
        verify(sseHub).emit(JobStatusEvent.of(job));
    }

    @Test
    void execute_shouldDispatchJob_toRegistry() {
        // Arrange
        Job job = runningJob(0, 3);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(registry.dispatch(job)).thenReturn(new JobResult(new EchoPayload("echo: hi")));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.execute(1L);

        // Assert
        verify(registry).dispatch(job);
    }

    @Test
    void execute_shouldRequeueWithBackoff_whenHandlerFailsBelowMaxAttempts() {
        // Arrange
        Job job = runningJob(0, 3);
        job.setLockedAt(NOW);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(registry.dispatch(job)).thenThrow(new RuntimeException("boom"));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.execute(1L);

        // Assert
        assertThat(job.getAttempts()).isEqualTo(1);
        assertThat(job.getError()).isEqualTo("boom");
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getNextRunAt()).isEqualTo(NOW.plusSeconds(props.getBackoffBaseSeconds()));
        assertThat(job.getLockedAt()).isNull();
        assertThat(job.getFinishedAt()).isNull();
        verify(sseHub).emit(JobStatusEvent.of(job));
    }

    @Test
    void execute_shouldMarkFailed_whenAttemptsReachMaxAttempts() {
        // Arrange
        Job job = runningJob(2, 3);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(registry.dispatch(job)).thenThrow(new RuntimeException("boom"));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.execute(1L);

        // Assert
        assertThat(job.getAttempts()).isEqualTo(3);
        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getFinishedAt()).isEqualTo(NOW);
        assertThat(job.getError()).isEqualTo("boom");
        verify(sseHub).emit(JobStatusEvent.of(job));
    }

    @Test
    void execute_backoffShouldGrowExponentially_withAttempts() {
        // Arrange
        Job jobAtFirstFailure = runningJob(0, 5);
        Job jobAtSecondFailure = runningJob(1, 5);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(jobAtFirstFailure));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(jobAtSecondFailure));
        when(registry.dispatch(any(Job.class))).thenThrow(new RuntimeException("boom"));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.execute(1L);
        service.execute(2L);

        // Assert — attempts=1 -> now+base, attempts=2 -> now+2*base.
        assertThat(jobAtFirstFailure.getNextRunAt()).isEqualTo(NOW.plusSeconds(props.getBackoffBaseSeconds()));
        assertThat(jobAtSecondFailure.getNextRunAt()).isEqualTo(NOW.plusSeconds(2 * props.getBackoffBaseSeconds()));
    }

    @Test
    void execute_shouldNotRethrow_whenHandlerThrows() {
        // Arrange
        Job job = runningJob(0, 3);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(registry.dispatch(job)).thenThrow(new RuntimeException("boom"));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act / Assert
        assertThatCode(() -> service.execute(1L)).doesNotThrowAnyException();
    }
}
