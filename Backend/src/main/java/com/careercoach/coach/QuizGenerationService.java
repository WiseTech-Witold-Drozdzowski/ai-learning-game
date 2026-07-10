package com.careercoach.coach;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.ai.OpenRouterClient;
import com.careercoach.ai.OpenRouterCompletion;
import com.careercoach.tasks.domain.Quiz;
import com.careercoach.tasks.domain.Task;
import com.careercoach.tasks.service.TaskService;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

/**
 * Coach-side quiz generation (issue-5): assembles the coach context, asks OpenRouter
 * (mocked in tests) for a quiz with an answer key, maps the reply to a {@link Quiz}
 * and persists it onto the task via {@link TaskService#saveQuiz}. Lives in
 * {@code coach} (like {@code PlanningService}) so {@code tasks} never depends on
 * {@code coach} — {@code coach → tasks} is the only direction.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class QuizGenerationService {

    private final ContextAssembler contextAssembler;
    private final OpenRouterClient openRouterClient;
    private final ObjectMapper objectMapper;
    private final TaskService taskService;

    /** Generate + persist a quiz for {@code taskId}; returns the stored quiz (with answer key). */
    public Quiz generate(Long taskId) {
        Task task = taskService.get(taskId);
        OpenRouterCompletion completion = openRouterClient.complete(buildPrompt(task));
        QuizLlmResponse parsed = objectMapper.readValue(completion.content(), QuizLlmResponse.class);

        List<QuizLlmResponse.Item> items = parsed.questions() == null ? List.of() : parsed.questions();
        List<Quiz.Question> questions = items.stream()
                .map(item -> new Quiz.Question(item.prompt(), item.options(), item.answer()))
                .toList();

        Quiz quiz = new Quiz(questions);
        taskService.saveQuiz(taskId, quiz);
        return quiz;
    }

    private String buildPrompt(Task task) {
        return contextAssembler.assemble(task.getGoalId())
                + "\n\n## Generate a quiz for this task\n" + task.getTitle()
                + "\n\nReturn JSON only, shape: "
                + "{\"questions\":[{\"prompt\":\"...\",\"options\":[\"...\"],\"answer\":\"...\"}]}. "
                + "The \"answer\" of each question must be exactly one of its \"options\".";
    }
}
