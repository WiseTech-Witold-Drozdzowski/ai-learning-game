package com.careercoach.jobs;

/** SSE payload for a status transition. */
public record JobStatusEvent(Long jobId, JobType type, JobStatus status) {

    public static JobStatusEvent of(Job job) {
        return new JobStatusEvent(job.getId(), job.getType(), job.getStatus());
    }
}
