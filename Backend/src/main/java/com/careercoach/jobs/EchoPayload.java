package com.careercoach.jobs;

/** Trivial typed payload used by the tracer {@link JobType#ECHO} handler. */
public record EchoPayload(String message) implements JobPayload {
}
