package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link JobRunner} (issue-1) — poll orchestration: claim then
 * dispatch, emitting RUNNING per claimed job. The executor is a synchronous
 * stub so dispatch is observed inline.
 */
@ExtendWith(MockitoExtension.class)
class JobRunnerTest {

    @Mock
    private JobClaimService claimService;

    @Mock
    private JobExecutionService executionService;

    @Mock
    private SseHub sseHub;

    private Executor executor;
    private JobRunner runner;

    @BeforeEach
    void setUp() {
        executor = Runnable::run;
        runner = new JobRunner(claimService, executionService, sseHub, executor);
    }

    private static Job jobWithId(Long id) {
        Job job = new Job(JobType.ECHO, JobStatus.RUNNING);
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    @Test
    void pollOnce_shouldDispatchAndEmitRunning_forEachClaimedJob() {
        // Arrange
        Job job1 = jobWithId(1L);
        Job job2 = jobWithId(2L);
        when(claimService.claimBatch()).thenReturn(List.of(job1, job2));

        // Act
        runner.pollOnce();

        // Assert
        ArgumentCaptor<Long> idCaptor = ArgumentCaptor.forClass(Long.class);
        verify(executionService, times(2)).execute(idCaptor.capture());
        assertThat(idCaptor.getAllValues()).containsExactly(1L, 2L);
        verify(sseHub, times(2)).emit(any(JobStatusEvent.class));
    }

    @Test
    void pollOnce_shouldDoNothing_whenNoJobsClaimed() {
        // Arrange
        when(claimService.claimBatch()).thenReturn(List.of());

        // Act
        runner.pollOnce();

        // Assert
        verifyNoInteractions(executionService, sseHub);
    }
}
