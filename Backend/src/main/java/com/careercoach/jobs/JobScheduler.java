package com.careercoach.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled trigger for the runner, gated by {@code careercoach.jobs.scheduler-enabled}
 * so tests disable it and drive {@link JobRunner#pollOnce()} deterministically.
 */
@Component
@ConditionalOnProperty(name = "careercoach.jobs.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class JobScheduler {

    private final JobRunner jobRunner;

    public JobScheduler(JobRunner jobRunner) {
        this.jobRunner = jobRunner;
    }

    @Scheduled(fixedDelayString = "${careercoach.jobs.poll-delay-ms:1000}")
    void scheduledPoll() {
        jobRunner.pollOnce();
    }
}
