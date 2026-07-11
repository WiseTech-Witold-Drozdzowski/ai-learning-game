package com.careercoach.jobs;

/** No {@link JobHandler} registered for the requested {@link JobType}. */
public class JobHandlerNotFoundException extends IllegalStateException {

    public JobHandlerNotFoundException(JobType type) {
        super("No JobHandler registered for type: " + type);
    }
}
