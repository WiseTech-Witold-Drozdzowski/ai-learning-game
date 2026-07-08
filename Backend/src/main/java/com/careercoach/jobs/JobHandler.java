package com.careercoach.jobs;

/**
 * Strategy contract — the ONLY thing the {@code jobs} module knows about
 * concrete job logic. Implementations live in other modules.
 *
 * <p>Parameterized on its input payload type {@code I} so implementations receive
 * an already-typed input and never cast {@code job.getInput()} themselves. The
 * single JSONB-to-{@code I} conversion happens once, centrally, in
 * {@link JobHandlerRegistry#dispatch(Job)}.
 *
 * @param <I> the concrete input payload this handler consumes
 */
public interface JobHandler<I extends JobPayload> {

    /** The {@link JobType} this handler serves. */
    JobType type();

    /** The concrete input payload class, used for the checked cast at dispatch. */
    Class<I> inputType();

    /**
     * Process the typed {@code input} and return the output. Throw on failure —
     * the runner converts exceptions to retry/backoff or FAILED.
     */
    JobResult handle(Job job, I input);
}
