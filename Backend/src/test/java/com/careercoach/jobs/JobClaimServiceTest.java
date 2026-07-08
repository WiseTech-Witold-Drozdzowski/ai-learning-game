package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link JobClaimService} (issue-1) — the claim/recovery seam.
 * {@link JobRepository} is mocked; {@link JobRunnerProperties} is a plain
 * value holder configured directly; {@link Clock} is fixed for determinism.
 */
@ExtendWith(MockitoExtension.class)
class JobClaimServiceTest {

    private static final Instant NOW = Instant.parse("2024-01-01T00:00:00Z");

    @Mock
    private JobRepository jobRepository;

    private JobRunnerProperties props;
    private Clock clock;
    private JobClaimService service;

    @BeforeEach
    void setUp() {
        props = new JobRunnerProperties();
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new JobClaimService(jobRepository, props, clock);
    }

    @Test
    void claimBatch_shouldMarkEligibleQueuedJobsRunning_andSetLockedAtStartedAt() {
        // Arrange
        Job job = Job.queued(JobType.ECHO, new EchoPayload("hi"), null, null, 3);
        when(jobRepository.findClaimableIds(eq(JobType.ECHO.name()), anyInt(), eq(NOW)))
                .thenReturn(List.of(1L));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<Job> claimed = service.claimBatch();

        // Assert
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(claimed.get(0).getLockedAt()).isEqualTo(NOW);
        assertThat(claimed.get(0).getStartedAt()).isEqualTo(NOW);
    }

    @Test
    void claimBatch_shouldReturnEmpty_andWriteNothing_whenNoQueuedJobs() {
        // Act
        List<Job> claimed = service.claimBatch();

        // Assert
        assertThat(claimed).isEmpty();
        verify(jobRepository, never()).save(any());
        verify(jobRepository, never()).findById(any());
    }

    @Test
    void claimBatch_shouldRespectPerTypeConcurrencyLimit_whenSlotsAreFull() {
        // Arrange
        props.setConcurrency(concurrencyOf(JobType.ECHO, 1));
        when(jobRepository.countByTypeAndStatus(JobType.ECHO, JobStatus.RUNNING)).thenReturn(1L);

        // Act
        List<Job> claimed = service.claimBatch();

        // Assert
        assertThat(claimed).isEmpty();
        verify(jobRepository, never()).findClaimableIds(eq(JobType.ECHO.name()), anyInt(), any());
    }

    @Test
    void claimBatch_shouldClaimExactlyOne_atTheConcurrencyLimitBoundary() {
        // Arrange
        props.setConcurrency(concurrencyOf(JobType.ECHO, 1));
        when(jobRepository.countByTypeAndStatus(JobType.ECHO, JobStatus.RUNNING)).thenReturn(0L);
        Job job = Job.queued(JobType.ECHO, new EchoPayload("hi"), null, null, 3);
        when(jobRepository.findClaimableIds(eq(JobType.ECHO.name()), eq(1), eq(NOW)))
                .thenReturn(List.of(1L));
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<Job> claimed = service.claimBatch();

        // Assert
        assertThat(claimed).hasSize(1);
    }

    @Test
    void claimBatch_shouldPassFixedNow_toTheClaimableQuery() {
        // Arrange
        Job job = Job.queued(JobType.ECHO, new EchoPayload("hi"), null, null, 3);
        when(jobRepository.findClaimableIds(eq(JobType.ECHO.name()), anyInt(), eq(NOW)))
                .thenReturn(List.of(2L));
        when(jobRepository.findById(2L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        List<Job> claimed = service.claimBatch();

        // Assert — the query itself encodes next_run_at <= now OR null; we only verify
        // the fixed `now` reached the query and that only its returned ids were claimed.
        verify(jobRepository).findClaimableIds(eq(JobType.ECHO.name()), anyInt(), eq(NOW));
        assertThat(claimed).hasSize(1);
    }

    @Test
    void recoverStuck_shouldRequeueStuckRunningJob_belowMaxAttempts() {
        // Arrange
        Job stuck = new Job(JobType.ECHO, JobStatus.RUNNING);
        stuck.setAttempts(0);
        stuck.setMaxAttempts(3);
        stuck.setLockedAt(NOW.minusSeconds(1000));
        when(jobRepository.findByStatusAndLockedAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(List.of(stuck));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.recoverStuck();

        // Assert
        assertThat(stuck.getAttempts()).isEqualTo(1);
        assertThat(stuck.getLockedAt()).isNull();
        assertThat(stuck.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(stuck.getNextRunAt()).isEqualTo(NOW);
    }

    @Test
    void recoverStuck_shouldLeaveJobUntouched_whenWithinTimeout() {
        // Arrange
        when(jobRepository.findByStatusAndLockedAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(List.of());

        // Act
        service.recoverStuck();

        // Assert
        verify(jobRepository, never()).save(any());
    }

    @Test
    void recoverStuck_shouldMarkFailed_whenIncrementedAttemptsReachMaxAttempts() {
        // Arrange
        Job stuck = new Job(JobType.ECHO, JobStatus.RUNNING);
        stuck.setAttempts(2);
        stuck.setMaxAttempts(3);
        stuck.setLockedAt(NOW.minusSeconds(1000));
        when(jobRepository.findByStatusAndLockedAtBefore(eq(JobStatus.RUNNING), any()))
                .thenReturn(List.of(stuck));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.recoverStuck();

        // Assert
        assertThat(stuck.getAttempts()).isEqualTo(3);
        assertThat(stuck.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(stuck.getFinishedAt()).isEqualTo(NOW);
    }

    private static Map<JobType, Integer> concurrencyOf(JobType type, int limit) {
        Map<JobType, Integer> map = new EnumMap<>(JobType.class);
        map.put(type, limit);
        return map;
    }
}
