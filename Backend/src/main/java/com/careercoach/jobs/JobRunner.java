package com.careercoach.jobs;

import java.util.List;
import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Poll orchestration: claim a batch, emit RUNNING per claimed job, and dispatch
 * each to the executor. Deterministically callable via {@link #pollOnce()}.
 */
@Component
@RequiredArgsConstructor
public class JobRunner {

    private final JobClaimService claimService;
    private final JobExecutionService executionService;
    private final SseHub sseHub;
    @Qualifier("jobExecutor")
    private final Executor executor;

    public void pollOnce() {
        List<Job> claimed = claimService.claimBatch();
        for (Job job : claimed) {
            sseHub.emit(JobStatusEvent.of(job));
            Long id = job.getId();
            executor.execute(() -> executionService.execute(id));
        }
    }
}
