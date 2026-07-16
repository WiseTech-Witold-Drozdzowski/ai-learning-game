package com.careercoach.coach.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.coach.domain.MockMessage;
import com.careercoach.coach.repository.MockMessageRepository;
import com.careercoach.coach.repository.MockSessionRepository;
import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.TaskTypeDefinitionService;
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
import com.careercoach.coach.domain.EvaluationOutput;
import com.careercoach.coach.domain.SkillBreakdown;

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
    private final TaskTypeDefinitionService taskTypeDefinitionService;
    private final QuizGrader quizGrader;
    private final MockMessageRepository mockMessageRepository;
    private final MockSessionRepository mockSessionRepository;
    private final CoachNoteService coachNoteService;

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
        TaskTypeDefinition typeDefinition = taskTypeDefinitionService.get(input.typeKey());

        // Specialize by verification method: AUTO_QUIZ grades deterministically against the
        // stored answer key (no AI call); AI_DIALOG grades the persisted mock transcript;
        // AI_ARTIFACT_REVIEW asks OpenRouter to grade the submitted artifact.
        EvaluationOutput output = switch (typeDefinition.getVerificationMethod()) {
            case AUTO_QUIZ ->
                    quizGrader.grade(task.getQuiz(), input.answers(), typeDefinition, task.getSkillKeys());
            case AI_DIALOG -> gradeTranscript(task, input);
            default -> gradeArtifact(task, input);
        };

        // For AI_DIALOG, record the grade back onto the session so the review view shows it.
        if (input.mockSessionId() != null) {
            mockSessionRepository.findById(input.mockSessionId()).ifPresent(session -> {
                session.setScore(output.score());
                mockSessionRepository.save(session);
            });
        }

        // The AI only proposes; award clamps + de-duplicates (idempotency key = jobId),
        // then the backend sets the terminal task state from the verdict.
        AwardResult award = award(job, task, input, output.skillBreakdown());
        taskService.recordEvaluation(task.getId(), output.passed(), award.totalGranted());

        log.info("EVALUATION job {} graded task {}: score={} passed={} expGranted={}",
                job.getId(), task.getId(), output.score(), output.passed(), award.totalGranted());
        return new JobResult(output);
    }

    private EvaluationOutput gradeArtifact(Task task, EvaluationInput input) {
        return gradeWithLlm(buildPrompt(task, input));
    }

    /** AI_DIALOG (issue-6): grade the persisted mock transcript against the assembled context. */
    private EvaluationOutput gradeTranscript(Task task, EvaluationInput input) {
        return gradeWithLlm(buildTranscriptPrompt(task, input));
    }

    private EvaluationOutput gradeWithLlm(String prompt) {
        OpenRouterCompletion completion = openRouterClient.complete(prompt);
        EvaluationLlmResponse parsed = objectMapper.readValue(completion.content(), EvaluationLlmResponse.class);

        // Autonomous coach memory: persist the distillate the coach chose to remember. Only
        // this note content survives (e.g. after a mock) — never the raw material graded here.
        coachNoteService.applyOps(parsed.coachNotes());

        List<SkillBreakdown> breakdown = toBreakdown(parsed);
        long expProposed = breakdown.stream().mapToLong(SkillBreakdown::exp).sum();
        return new EvaluationOutput(parsed.score(), expProposed, breakdown, parsed.feedback(), parsed.passed());
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

    /**
     * Build the AI_DIALOG grading prompt: the assembled context (which deliberately
     * excludes transcripts) plus the mock transcript appended here explicitly, so the
     * grader sees the conversation without it ever entering long-term coach memory.
     */
    private String buildTranscriptPrompt(Task task, EvaluationInput input) {
        StringBuilder sb = new StringBuilder(contextAssembler.assemble(task.getGoalId()));
        sb.append("\n\n## Task under evaluation\n").append(task.getTitle());
        sb.append("\n\n## Mock interview transcript\n");
        List<MockMessage> transcript = input.mockSessionId() == null
                ? List.of()
                : mockMessageRepository.findBySessionIdOrderBySeqAsc(input.mockSessionId());
        for (MockMessage message : transcript) {
            sb.append(message.getRole()).append(": ").append(message.getContent()).append('\n');
        }
        return sb.toString();
    }
}
