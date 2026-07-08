package com.careercoach.jobs;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** Application service to enqueue and read jobs. */
@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final JobRunnerProperties props;

    /** Persist a QUEUED job (attempts=0, maxAttempts=default). Related ids may be null. */
    public Job enqueue(JobType type, JobPayload input, Long relatedGoalId, Long relatedTaskId) {
        Job job = Job.queued(type, input, relatedGoalId, relatedTaskId, props.getDefaultMaxAttempts());
        return jobRepository.save(job);
    }

    public Job get(Long id) {
        return jobRepository.findById(id).orElseThrow(() -> new JobNotFoundException(id));
    }
}
