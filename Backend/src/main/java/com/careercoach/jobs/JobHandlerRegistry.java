package com.careercoach.jobs;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Resolves a {@link JobHandler} by {@link JobType} (strategy dispatch) and owns
 * the single JSONB-to-input conversion in {@link #dispatch(Job)}.
 */
@Component
public class JobHandlerRegistry {

    private final Map<JobType, JobHandler<?>> handlers = new EnumMap<>(JobType.class);

    public JobHandlerRegistry(List<JobHandler<?>> handlers) {
        for (JobHandler<?> handler : handlers) {
            JobHandler<?> existing = this.handlers.putIfAbsent(handler.type(), handler);
            if (existing != null) {
                throw new IllegalStateException(
                        "Duplicate JobHandler for type " + handler.type()
                                + ": " + existing.getClass().getName()
                                + " and " + handler.getClass().getName());
            }
        }
    }

    public JobHandler<?> handlerFor(JobType type) {
        JobHandler<?> handler = handlers.get(type);
        if (handler == null) {
            throw new JobHandlerNotFoundException(type);
        }
        return handler;
    }

    /** Resolve the handler for the job's type and invoke it with a typed input. */
    public JobResult dispatch(Job job) {
        return dispatchTyped(handlerFor(job.getType()), job);
    }

    /**
     * Captures the handler's wildcard into a named type variable so the input can
     * be converted with a checked {@link Class#cast(Object)} — no unchecked cast.
     */
    private <I extends JobPayload> JobResult dispatchTyped(JobHandler<I> handler, Job job) {
        I input = handler.inputType().cast(job.getInput());
        return handler.handle(job, input);
    }
}
