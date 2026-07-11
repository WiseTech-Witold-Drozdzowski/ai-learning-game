package com.careercoach.coach.service;

import java.util.List;

/**
 * Parse target for the quiz-generator LLM reply (mocked in tests). The model returns
 * a list of questions, each with a {@code prompt}, selectable {@code options} and the
 * correct {@code answer} (which becomes the answer key on the stored quiz).
 */
public record QuizLlmResponse(List<Item> questions) {

    public record Item(String prompt, List<String> options, String answer) {
    }
}
