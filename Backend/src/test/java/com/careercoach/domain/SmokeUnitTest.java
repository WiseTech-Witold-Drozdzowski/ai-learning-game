package com.careercoach.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;

/**
 * TEMPORARY pure unit test (no Spring context) — confirms JUnit/Gradle work
 * and run fast, without a database.
 */
class SmokeUnitTest {

    @Test
    void newJobKeepsTypeAndStatus() {
        Job job = new Job(JobType.PLANNING, JobStatus.QUEUED);

        assertThat(job.getType()).isEqualTo(JobType.PLANNING);
        assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(job.getId()).isNull(); // not persisted yet
    }

    @Test
    void statusIsMutable() {
        Job job = new Job(JobType.AGENT, JobStatus.QUEUED);

        job.setStatus(JobStatus.RUNNING);

        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
    }
}
