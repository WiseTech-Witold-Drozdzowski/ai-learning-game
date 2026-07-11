package com.careercoach.coach.web;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.coach.service.QuizGenerationService;
import com.careercoach.tasks.web.model.QuizView;

import lombok.RequiredArgsConstructor;

/**
 * {@code POST /api/tasks/{id}/quiz} — generate + persist an AUTO_QUIZ quiz and return
 * it <em>without</em> the answer key ({@link QuizView}). Lives in {@code coach} (not
 * {@code tasks}) so the tasks module never depends on coach — generation is AI logic.
 */
@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class QuizController {

    private final QuizGenerationService quizGenerationService;

    @PostMapping("/{id}/quiz")
    public QuizView generateQuiz(@PathVariable Long id) {
        return QuizView.from(quizGenerationService.generate(id));
    }
}
