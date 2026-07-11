package com.careercoach.coach.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.goals.domain.Goal;
import com.careercoach.goals.service.GoalService;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobHandler;
import com.careercoach.jobs.JobResult;
import com.careercoach.jobs.JobType;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;
import com.careercoach.coach.domain.PlanningOutput;
import com.careercoach.coach.domain.ProposedGoal;
import com.careercoach.coach.domain.ProposedTask;

/**
 * Handles {@link JobType#PLANNING} (BACKEND_DESIGN §4): assembles context, calls
 * OpenRouter, parses the structured response and maps {@code input → output} —
 * DECOMPOSE persists {@code PROPOSED} sub-goals and reports them; GENERATE_TASKS
 * reports task proposals (created as real tasks only on acceptance).
 */
@Component
@RequiredArgsConstructor
public class PlanningJobHandler implements JobHandler<PlanningInput> {

    private final ContextAssembler contextAssembler;
    private final OpenRouterClient openRouterClient;
    private final GoalService goalService;
    private final ObjectMapper objectMapper;

    @Override
    public JobType type() {
        return JobType.PLANNING;
    }

    @Override
    public Class<PlanningInput> inputType() {
        return PlanningInput.class;
    }

    @Override
    public JobResult handle(Job job, PlanningInput input) {
        String prompt = contextAssembler.assemble(input.goalId());
        OpenRouterCompletion completion = openRouterClient.complete(prompt);
        PlanningLlmResponse parsed = objectMapper.readValue(completion.content(), PlanningLlmResponse.class);

        return switch (input.mode()) {
            case DECOMPOSE -> new JobResult(new PlanningOutput(persistProposedGoals(input.goalId(), parsed), List.of()));
            case GENERATE_TASKS -> new JobResult(new PlanningOutput(List.of(), toProposedTasks(parsed)));
        };
    }

    private List<ProposedGoal> persistProposedGoals(Long parentGoalId, PlanningLlmResponse parsed) {
        List<PlanningLlmResponse.GoalItem> items = parsed.goals() == null ? List.of() : parsed.goals();
        List<ProposedGoal> proposed = new ArrayList<>();
        for (PlanningLlmResponse.GoalItem item : items) {
            Goal saved = goalService.createProposedChild(parentGoalId, item.title(), item.description());
            proposed.add(new ProposedGoal(saved.getId(), saved.getTitle(), saved.getDescription()));
        }
        return proposed;
    }

    private List<ProposedTask> toProposedTasks(PlanningLlmResponse parsed) {
        List<PlanningLlmResponse.TaskItem> items = parsed.tasks() == null ? List.of() : parsed.tasks();
        return items.stream()
                .map(item -> new ProposedTask(item.title(), item.description(), item.typeKey(), item.skillKeys()))
                .toList();
    }
}
