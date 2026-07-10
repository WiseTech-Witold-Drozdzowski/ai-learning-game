package com.careercoach.coach;

import org.springframework.stereotype.Component;

import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobService;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.service.AiVerificationLauncher;

import lombok.RequiredArgsConstructor;

/**
 * Coach-side implementation of the {@code tasks} {@link AiVerificationLauncher}
 * port: builds an {@link EvaluationInput} from the submitted task and enqueues an
 * EVALUATION job. Depends only on {@link JobService} (no {@code TaskService}), so
 * wiring it into {@code TaskService} creates no constructor cycle.
 */
@Component
@RequiredArgsConstructor
public class EvaluationLauncher implements AiVerificationLauncher {

    private final JobService jobService;

    @Override
    public Long launchEvaluation(Task task, String artifact) {
        EvaluationInput input = new EvaluationInput(task.getId(), task.getTypeKey(), artifact, null);
        Job job = jobService.enqueue(JobType.EVALUATION, input, task.getGoalId(), task.getId());
        return job.getId();
    }
}
