package com.careercoach.jobs.web;

import java.time.Instant;

import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobPayload;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;

/** REST view of a Job. */
public record JobResponse(Long id, JobType type, JobStatus status, int attempts,
                          int maxAttempts, String error, JobPayload output,
                          Instant createdAt, Instant startedAt, Instant finishedAt) {

    public static JobResponse from(Job job) {
        return new JobResponse(job.getId(), job.getType(), job.getStatus(),
                job.getAttempts(), job.getMaxAttempts(), job.getError(), job.getOutput(),
                job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
    }
}
