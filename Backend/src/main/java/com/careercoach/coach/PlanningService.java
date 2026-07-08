package com.careercoach.coach;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.domain.GoalState;
import com.careercoach.goals.service.GoalService;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobService;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.service.TaskService;

import lombok.RequiredArgsConstructor;

/**
 * Coach-facing planning use cases (BACKEND_DESIGN §4): enqueue a PLANNING job and
 * close the proposal loop by turning accepted task proposals into real tasks.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class PlanningService {

    private final GoalService goalService;
    private final TaskService taskService;
    private final JobService jobService;

    /**
     * Enqueue a PLANNING job for {@code goalId}. GENERATE_TASKS is rejected unless
     * the goal is {@code ACTIVE} (the coach only generates tasks under active goals).
     */
    public Job plan(Long goalId, PlanningMode mode) {
        Goal goal = goalService.get(goalId);
        if (mode == PlanningMode.GENERATE_TASKS && goal.getState() != GoalState.ACTIVE) {
            throw new IllegalPlanningStateException(goalId, goal.getState());
        }
        return jobService.enqueue(JobType.PLANNING, new PlanningInput(goalId, mode), goalId, null);
    }

    /**
     * Accept task proposals under an {@code ACTIVE} goal, creating real {@code TODO}
     * tasks. Returns the created tasks.
     */
    public List<Task> acceptTaskProposals(Long goalId, List<ProposedTask> proposals) {
        Goal goal = goalService.get(goalId);
        if (goal.getState() != GoalState.ACTIVE) {
            throw new IllegalPlanningStateException(
                    "Cannot create tasks under goal " + goalId + " in state " + goal.getState() + " (must be ACTIVE)");
        }
        List<Task> created = new ArrayList<>();
        for (ProposedTask proposal : proposals) {
            created.add(taskService.createTodo(goalId, proposal.title(), proposal.description(),
                    proposal.typeKey(), proposal.skillKeys()));
        }
        return created;
    }
}
