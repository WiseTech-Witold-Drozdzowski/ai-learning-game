package com.careercoach.tasks.web.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careercoach.tasks.domain.Quiz;

/**
 * Unit test for {@link QuizView#from} (issue-5): the client-facing projection must
 * carry prompts + options but <em>never</em> the answer key. This is the invariant
 * that stops the user self-cheating — guarded here so removing it fails a test.
 */
class QuizViewTest {

    @Test
    void from_shouldExposePromptsAndOptions_butStripAnswerKey() {
        // Arrange — a quiz whose questions carry the correct answers
        Quiz quiz = new Quiz(List.of(
                new Quiz.Question("Q1", List.of("A", "B"), "A"),
                new Quiz.Question("Q2", List.of("C", "D"), "D")));

        // Act
        QuizView view = QuizView.from(quiz);

        // Assert — prompts and options survive
        assertThat(view.questions()).hasSize(2);
        assertThat(view.questions().get(0).prompt()).isEqualTo("Q1");
        assertThat(view.questions().get(0).options()).containsExactly("A", "B");
        assertThat(view.questions().get(1).options()).containsExactly("C", "D");

        // Assert — the QuestionView type has no answer component at all (structural guarantee)
        List<String> components = Arrays.stream(QuizView.QuestionView.class.getRecordComponents())
                .map(RecordComponent::getName).toList();
        assertThat(components).containsExactlyInAnyOrder("prompt", "options");
        assertThat(components).doesNotContain("answer");
    }

    @Test
    void from_shouldReturnEmptyView_whenQuizIsNull() {
        // Arrange / Act — a missing quiz must not throw
        QuizView view = QuizView.from(null);

        // Assert
        assertThat(view.questions()).isEmpty();
    }
}
