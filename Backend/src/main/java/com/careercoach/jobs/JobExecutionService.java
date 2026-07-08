package com.careercoach.jobs;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Transactional execution of a single claimed job: run the handler, persist the
 * terminal/retry state, and emit the SSE transition. Never rethrows onto the
 * executor thread.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobExecutionService {

    private final JobRepository jobRepository;
    private final JobHandlerRegistry registry;
    private final SseHub sseHub;
    private final JobRunnerProperties props;
    private final Clock clock;

    @Transactional
    public void execute(Long jobId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) {
            log.warn("Job {} vanished before execution", jobId);
            return;
        }
        Instant now = Instant.now(clock);
        try {
            JobResult result = registry.dispatch(job);
            markDone(job, result, now);
        } catch (Exception ex) {
            log.warn("Job {} failed on attempt {}", jobId, job.getAttempts() + 1, ex);
            markFailedOrRetry(job, ex, now);
        }
        // Both outcomes persist the mutated job and announce the transition.
        jobRepository.save(job);
        sseHub.emit(JobStatusEvent.of(job));
    }

    private void markDone(Job job, JobResult result, Instant now) {
        job.setOutput(result.output());
        job.setStatus(JobStatus.DONE);
        job.setFinishedAt(now);
        job.setError(null);
    }

    private void markFailedOrRetry(Job job, Exception ex, Instant now) {
        job.setAttempts(job.getAttempts() + 1);
        job.setError(ex.getMessage());
        job.setLockedAt(null);
        if (job.getAttempts() >= job.getMaxAttempts()) {
            job.setStatus(JobStatus.FAILED);
            job.setFinishedAt(now);
        } else {
            job.setStatus(JobStatus.QUEUED);
            job.setNextRunAt(now.plusSeconds(backoffSeconds(job.getAttempts())));
        }
    }

    private long backoffSeconds(int attempts) {
        return props.getBackoffBaseSeconds() * (1L << (attempts - 1));
    }
}
