package com.careercoach.jobs;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Transactional claim + recovery of jobs — the core claim-logic seam. The claim
 * query ({@code FOR UPDATE SKIP LOCKED}) and the flip to RUNNING commit together.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobClaimService {

    private final JobRepository jobRepository;
    private final JobRunnerProperties props;
    private final Clock clock;

    /**
     * Recover stuck jobs, then claim up to the per-type concurrency slots (capped
     * by batch size), marking them RUNNING with {@code lockedAt}/{@code startedAt}.
     * Each type is claimed independently so a problem with one type (e.g. a
     * misbehaving query for a not-yet-supported type) can't block the others.
     */
    @Transactional
    public List<Job> claimBatch() {
        recoverStuck();

        Instant now = Instant.now(clock);
        List<Job> claimed = new ArrayList<>();

        for (JobType type : JobType.values()) {
            if (claimed.size() >= props.getBatchSize()) {
                break;
            }
            try {
                claimForType(type, now, claimed);
            } catch (RuntimeException ex) {
                log.warn("Failed to claim jobs of type {}", type, ex);
            }
        }
        return claimed;
    }

    private void claimForType(JobType type, Instant now, List<Job> claimed) {
        int slots = props.limitFor(type) - (int) jobRepository.countByTypeAndStatus(type, JobStatus.RUNNING);
        slots = Math.min(slots, props.getBatchSize() - claimed.size());
        if (slots <= 0) {
            return;
        }

        List<Long> ids = jobRepository.findClaimableIds(type.name(), slots, now);
        if (!ids.isEmpty()) {
            log.debug("Claiming {} {} job(s): {}", ids.size(), type, ids);
        }
        for (Long id : ids) {
            Job job = jobRepository.findById(id).orElse(null);
            if (job == null) {
                continue;
            }
            job.setStatus(JobStatus.RUNNING);
            job.setLockedAt(now);
            if (job.getStartedAt() == null) {
                job.setStartedAt(now);
            }
            claimed.add(jobRepository.save(job));
        }
    }

    /**
     * Requeue RUNNING jobs whose {@code lockedAt} predates the timeout: increment
     * attempts, clear the lock; FAILED once attempts reach maxAttempts, else QUEUED
     * with {@code nextRunAt=now}.
     */
    @Transactional
    public void recoverStuck() {
        Instant now = Instant.now(clock);
        Instant threshold = now.minusSeconds(props.getRunningTimeoutSeconds());

        for (Job job : jobRepository.findByStatusAndLockedAtBefore(JobStatus.RUNNING, threshold)) {
            job.setAttempts(job.getAttempts() + 1);
            job.setLockedAt(null);
            if (job.getAttempts() >= job.getMaxAttempts()) {
                job.setStatus(JobStatus.FAILED);
                job.setFinishedAt(now);
            } else {
                job.setStatus(JobStatus.QUEUED);
                job.setNextRunAt(now);
            }
            jobRepository.save(job);
        }
    }
}
