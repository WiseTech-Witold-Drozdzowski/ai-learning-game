package com.careercoach.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.coach.repository.MockMessageRepository;
import com.careercoach.coach.repository.MockSessionRepository;
import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.gamification.domain.AvatarState;
import com.careercoach.gamification.domain.CareerProfile;
import com.careercoach.gamification.service.AwardCommand;
import com.careercoach.gamification.service.AwardResult;
import com.careercoach.gamification.service.CareerProfileService;
import com.careercoach.gamification.service.GamificationService;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobResult;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;
import com.careercoach.tasks.domain.Quiz;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.domain.TaskState;
import com.careercoach.tasks.service.TaskService;

import tools.jackson.databind.json.JsonMapper;
import com.careercoach.coach.domain.EvaluationOutput;
import com.careercoach.coach.domain.SkillBreakdown;

/**
 * Unit test for {@link EvaluationJobHandler} (BACKEND_DESIGN §4 / §5): maps
 * {@code input → output} against a mocked OpenRouter port, hands the proposed exp
 * to {@link GamificationService#award} (idempotency key = jobId) and transitions
 * the task to {@code DONE}/{@code REJECTED} via {@link TaskService#recordEvaluation}.
 * Red phase: {@code type}/{@code handle} throw {@code UnsupportedOperationException}.
 */
class EvaluationJobHandlerTest {

    private final ContextAssembler assembler = mock(ContextAssembler.class);
    private final OpenRouterClient openRouterClient = mock(OpenRouterClient.class);
    private final GamificationService gamificationService = mock(GamificationService.class);
    private final CareerProfileService careerProfileService = mock(CareerProfileService.class);
    private final TaskService taskService = mock(TaskService.class);
    private final TaskTypeDefinitionService taskTypeDefinitionService = mock(TaskTypeDefinitionService.class);
    private final QuizGrader quizGrader = mock(QuizGrader.class);
    private final MockMessageRepository mockMessageRepository = mock(MockMessageRepository.class);
    private final MockSessionRepository mockSessionRepository = mock(MockSessionRepository.class);
    private final EvaluationJobHandler handler = new EvaluationJobHandler(
            assembler, openRouterClient, JsonMapper.builder().build(),
            gamificationService, careerProfileService, taskService,
            taskTypeDefinitionService, quizGrader, mockMessageRepository, mockSessionRepository);

    private static Task task() {
        return Task.builder()
                .id(10L).goalId(5L).typeKey("AI_REVIEW").title("Build a REST API")
                .state(TaskState.IN_PROGRESS).skillKeys(List.of("JAVA")).expAwarded(0L)
                .build();
    }

    private Job runningJob() {
        Job job = new Job(JobType.EVALUATION, JobStatus.RUNNING);
        job.setId(555L);
        return job;
    }

    private void arrangeCommon(String llmJson) {
        when(taskService.get(10L)).thenReturn(task());
        when(taskTypeDefinitionService.get("AI_REVIEW")).thenReturn(TaskTypeDefinition.builder()
                .key("AI_REVIEW").displayName("AI review")
                .verificationMethod(VerificationMethod.AI_ARTIFACT_REVIEW)
                .expBase(50).expScaleByScore(false).requiresArtifact(false).build());
        when(assembler.assemble(5L)).thenReturn("CONTEXT");
        when(openRouterClient.complete(anyString())).thenReturn(new OpenRouterCompletion(llmJson));
        when(careerProfileService.getSingle())
                .thenReturn(Optional.of(new CareerProfile(1L, 0L, 1, AvatarState.initial())));
    }

    @Test
    void type_and_inputType_shouldIdentifyEvaluation() {
        assertThat(handler.type()).isEqualTo(JobType.EVALUATION);
        assertThat(handler.inputType()).isEqualTo(EvaluationInput.class);
    }

    @Test
    void handle_shouldMapOutputAwardExpAndCompleteTask_whenPassed() {
        // Arrange
        arrangeCommon("{\"score\":85,\"passed\":true,\"feedback\":\"Solid work\","
                + "\"skills\":[{\"skillKey\":\"JAVA\",\"exp\":40}]}");
        when(gamificationService.award(any(AwardCommand.class)))
                .thenReturn(AwardResult.builder().applied(true).totalGranted(40L).build());
        when(taskService.recordEvaluation(eq(10L), eq(true), eq(40L)))
                .thenReturn(Task.builder().id(10L).state(TaskState.DONE).expAwarded(40L).build());
        EvaluationInput input = new EvaluationInput(10L, "AI_REVIEW", "my artifact", null);

        // Act
        JobResult result = handler.handle(runningJob(), input);

        // Assert — output mapping
        EvaluationOutput output = (EvaluationOutput) result.output();
        assertThat(output.score()).isEqualTo(85);
        assertThat(output.passed()).isTrue();
        assertThat(output.feedback()).isEqualTo("Solid work");
        assertThat(output.expProposed()).isEqualTo(40L);
        assertThat(output.skillBreakdown()).singleElement()
                .satisfies(b -> {
                    assertThat(b.skillKey()).isEqualTo("JAVA");
                    assertThat(b.exp()).isEqualTo(40L);
                });

        // Assert — award command (AI proposes, jobId is the idempotency attempt key)
        ArgumentCaptor<AwardCommand> cmdCaptor = ArgumentCaptor.forClass(AwardCommand.class);
        verify(gamificationService).award(cmdCaptor.capture());
        AwardCommand cmd = cmdCaptor.getValue();
        assertThat(cmd.userId()).isEqualTo(1L);
        assertThat(cmd.sourceTaskId()).isEqualTo(10L);
        assertThat(cmd.attemptId()).isEqualTo(555L);
        assertThat(cmd.typeKey()).isEqualTo("AI_REVIEW");
        assertThat(cmd.goalId()).isEqualTo(5L);
        assertThat(cmd.skillAwards()).singleElement().satisfies(a -> {
            assertThat(a.skillKey()).isEqualTo("JAVA");
            assertThat(a.expProposed()).isEqualTo(40);
        });

        // Assert — the backend, not the AI, sets the terminal state, using the clamped grant
        verify(taskService).recordEvaluation(10L, true, 40L);
    }

    @Test
    void handle_shouldGradeViaQuizGraderNotLlm_whenAutoQuiz() {
        // Arrange — an AUTO_QUIZ task with a stored quiz + answer key
        Task quizTask = Task.builder()
                .id(10L).goalId(5L).typeKey("QUIZ").title("Java quiz")
                .state(TaskState.IN_PROGRESS).skillKeys(List.of("JAVA")).expAwarded(0L)
                .quiz(new Quiz(List.of(new Quiz.Question("q", List.of("A", "B"), "A"))))
                .build();
        when(taskService.get(10L)).thenReturn(quizTask);
        when(taskTypeDefinitionService.get("QUIZ")).thenReturn(TaskTypeDefinition.builder()
                .key("QUIZ").displayName("Quiz").verificationMethod(VerificationMethod.AUTO_QUIZ)
                .expBase(40).expScaleByScore(true).requiresArtifact(false).build());
        EvaluationOutput graded = new EvaluationOutput(
                100, 40L, List.of(new SkillBreakdown("JAVA", 40L)), "Answered 1 of 1 correctly (100%).", true);
        when(quizGrader.grade(any(), any(), any(), any())).thenReturn(graded);
        when(careerProfileService.getSingle())
                .thenReturn(Optional.of(new CareerProfile(1L, 0L, 1, AvatarState.initial())));
        when(gamificationService.award(any(AwardCommand.class)))
                .thenReturn(AwardResult.builder().applied(true).totalGranted(40L).build());
        when(taskService.recordEvaluation(eq(10L), eq(true), eq(40L)))
                .thenReturn(Task.builder().id(10L).state(TaskState.DONE).expAwarded(40L).build());
        EvaluationInput input = new EvaluationInput(10L, "QUIZ", null, List.of("A"));

        // Act
        JobResult result = handler.handle(runningJob(), input);

        // Assert — graded deterministically by the QuizGrader, the LLM is never consulted
        EvaluationOutput output = (EvaluationOutput) result.output();
        assertThat(output.score()).isEqualTo(100);
        assertThat(output.passed()).isTrue();
        verify(quizGrader).grade(any(), any(), any(), any());
        verifyNoInteractions(openRouterClient);

        // Assert — the quiz-proposed exp flows through award and the backend sets the terminal state
        ArgumentCaptor<AwardCommand> cmdCaptor = ArgumentCaptor.forClass(AwardCommand.class);
        verify(gamificationService).award(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue().skillAwards()).singleElement().satisfies(a -> {
            assertThat(a.skillKey()).isEqualTo("JAVA");
            assertThat(a.expProposed()).isEqualTo(40);
        });
        verify(taskService).recordEvaluation(10L, true, 40L);
    }

    @Test
    void handle_shouldRejectTask_whenFailed() {
        // Arrange
        arrangeCommon("{\"score\":30,\"passed\":false,\"feedback\":\"Needs work\","
                + "\"skills\":[{\"skillKey\":\"JAVA\",\"exp\":0}]}");
        when(gamificationService.award(any(AwardCommand.class)))
                .thenReturn(AwardResult.builder().applied(true).totalGranted(0L).build());
        when(taskService.recordEvaluation(eq(10L), eq(false), eq(0L)))
                .thenReturn(Task.builder().id(10L).state(TaskState.REJECTED).expAwarded(0L).build());
        EvaluationInput input = new EvaluationInput(10L, "AI_REVIEW", "weak artifact", null);

        // Act
        JobResult result = handler.handle(runningJob(), input);

        // Assert
        EvaluationOutput output = (EvaluationOutput) result.output();
        assertThat(output.passed()).isFalse();
        verify(taskService).recordEvaluation(10L, false, 0L);
    }
}
