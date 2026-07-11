package com.careercoach.jobs.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.jobs.JobService;

import lombok.RequiredArgsConstructor;

/** GET /api/jobs/{id} — read job status (prefix applied via ApiPrefixWebConfig). */
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    @GetMapping("/{id}")
    public JobResponse get(@PathVariable Long id) {
        return JobResponse.from(jobService.get(id));
    }
}
