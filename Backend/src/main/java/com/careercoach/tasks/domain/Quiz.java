package com.careercoach.tasks.domain;

import java.util.List;

/**
 * AI-generated quiz persisted as part of a task's state (BACKEND_DESIGN §2.3 / §3,
 * issue-5). Stored as JSONB on {@link Task}; the {@code answer} of each question is
 * the answer key used for deterministic grading and is never exposed to the client
 * (the {@code quiz} field is {@code @JsonIgnore}d — see {@code QuizView} for the
 * display projection).
 */
public record Quiz(List<Question> questions) {

    /**
     * One multiple-choice question: the {@code prompt}, the selectable {@code options}
     * and the correct {@code answer} (the answer-key value, matched case-insensitively
     * against the user's submitted answer).
     */
    public record Question(String prompt, List<String> options, String answer) {
    }
}
