package com.careercoach.tasks.web.model;

import java.util.List;

import com.careercoach.tasks.domain.Quiz;

/**
 * Client-facing projection of a {@link Quiz}: prompts and options only, with the
 * answer key deliberately stripped so the user cannot self-cheat (issue-5).
 */
public record QuizView(List<QuestionView> questions) {

    public record QuestionView(String prompt, List<String> options) {
    }

    /** Project a stored {@link Quiz} to its answer-free view. */
    public static QuizView from(Quiz quiz) {
        List<Quiz.Question> questions = quiz == null || quiz.questions() == null ? List.of() : quiz.questions();
        return new QuizView(questions.stream()
                .map(q -> new QuestionView(q.prompt(), q.options()))
                .toList());
    }
}
