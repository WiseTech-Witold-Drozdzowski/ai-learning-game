package com.careercoach.coach;

import java.util.List;

import org.springframework.stereotype.Component;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.service.AwardCommand;
import com.careercoach.gamification.service.AwardResult;
import com.careercoach.gamification.service.CareerProfileService;
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.gamification.service.SkillAward;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobHandler;
import com.careercoach.jobs.JobResult;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.service.TaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

/**
 * Handles {@link JobType#EVALUATION} (BACKEND_DESIGN §4 / §5): assembles context,
 * calls OpenRouter to grade the task's artifact/answers, maps the reply to
 * {@link EvaluationOutput}, then hands the proposed exp to
 * {@link GamificationService#award} (which clamps + is idempotent) and transitions
 * the task to {@code DONE}/{@code REJECTED}. The AI only proposes; the backend awards.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EvaluationJobHandler implements JobHandler<EvaluationInput> {

    private final ContextAssembler contextAssembler;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final GamificationService gamificationService;
    private final CareerProfileService careerProfileService;
    private final TaskService taskService;

    @Override
    public JobType type() {
        return JobType.EVALUATION;
    }

    @Override
    public Class<EvaluationInput> inputType() {
        return EvaluationInput.class;
    }

    @Override
    public JobResult handle(Job job, EvaluationInput input) {
        Task task = taskService.get(input.taskId());
        OpenRouterCompletion completion = openRouterClient.complete(buildPrompt(task, input));
        EvaluationLlmResponse parsed = objectMapper.readValue(completion.content(), EvaluationLlmResponse.class);

        List<SkillBreakdown> breakdown = toBreakdown(parsed);
        long expProposed = breakdown.stream().mapToLong(SkillBreakdown::exp).sum();
        EvaluationOutput output = new EvaluationOutput(
                parsed.score(), expProposed, breakdown, parsed.feedback(), parsed.passed());

        // The AI only proposes; award clamps + de-duplicates (idempotency key = jobId),
        // then the backend sets the terminal task state from the verdict.
        AwardResult award = award(job, task, input, breakdown);
        taskService.recordEvaluation(task.getId(), parsed.passed(), award.totalGranted());

        log.info("EVALUATION job {} graded task {}: score={} passed={} expGranted={}",
                job.getId(), task.getId(), parsed.score(), parsed.passed(), award.totalGranted());
        return new JobResult(output);
    }

    private AwardResult award(Job job, Task task, EvaluationInput input, List<SkillBreakdown> breakdown) {
        Long userId = careerProfileService.getSingle()
                .map(CareerProfile::getUserId)
                .orElseThrow(() -> new IllegalStateException("No career profile provisioned for evaluation"));
        List<SkillAward> skillAwards = breakdown.stream()
                .map(b -> new SkillAward(b.skillKey(), (int) b.exp()))
                .toList();
        AwardCommand cmd = new AwardCommand(
                userId, task.getId(), job.getId(), input.typeKey(), task.getGoalId(), "evaluation", skillAwards);
        return gamificationService.award(cmd);
    }

    private List<SkillBreakdown> toBreakdown(EvaluationLlmResponse parsed) {
        List<EvaluationLlmResponse.SkillItem> items = parsed.skills() == null ? List.of() : parsed.skills();
        return items.stream().map(item -> new SkillBreakdown(item.skillKey(), item.exp())).toList();
    }

    private String buildPrompt(Task task, EvaluationInput input) {
        StringBuilder sb = new StringBuilder(contextAssembler.assemble(task.getGoalId()));
        sb.append("\n\n## Task under evaluation\n").append(task.getTitle());
        if (input.artifact() != null) {
            sb.append("\n\n## Submitted artifact\n").append(input.artifact());
        }
        return sb.toString();
    }
}
