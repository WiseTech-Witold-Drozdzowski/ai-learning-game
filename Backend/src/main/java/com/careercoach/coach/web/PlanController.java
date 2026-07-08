package com.careercoach.coach.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.coach.PlanningService;
import com.careercoach.jobs.web.JobResponse;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/**
 * {@code POST /api/goals/{id}/plan} — start a PLANNING job for a goal. Lives in
 * {@code coach} (not {@code goals}) so the goals module never depends on coach.
 * Status is observed through the global SSE stream / {@code GET /api/jobs/{id}}.
 */
@RestController
@RequestMapping("/goals")
@RequiredArgsConstructor
public class PlanController {

    private final PlanningService planningService;

    @PostMapping("/{id}/plan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public JobResponse plan(@PathVariable Long id, @Valid @RequestBody PlanRequest req) {
        return JobResponse.from(planningService.plan(id, req.mode()));
    }
}
