package com.careercoach.jobs;

import org.springframework.stereotype.Component;

/**
 * Trivial reference handler (no external calls) proving the full lifecycle for
 * {@link JobType#ECHO}. Echoes the input message into the output.
 */
@Component
public class EchoJobHandler implements JobHandler<EchoPayload> {

    @Override
    public JobType type() {
        return JobType.ECHO;
    }

    @Override
    public Class<EchoPayload> inputType() {
        return EchoPayload.class;
    }

    @Override
    public JobResult handle(Job job, EchoPayload input) {
        String message = input == null ? null : input.message();
        return new JobResult(new EchoPayload("echo: " + message));
    }
}
