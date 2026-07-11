package com.careercoach.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.tasks.domain.Quiz;
import com.careercoach.coach.domain.EvaluationOutput;
import com.careercoach.coach.domain.SkillBreakdown;

/**
 * Unit test for {@link QuizGrader} (issue-5): deterministic scoring of quiz answers
 * against the stored answer key — percentages, the pass threshold and exp scaling.
 * The AI only generated the quiz; grading is pure code. Red phase: {@code grade}
 * throws {@code UnsupportedOperationException}.
 */
class QuizGraderTest {

    private final QuizGrader grader = new QuizGrader();

    private static Quiz.Question question(String answer) {
        return new Quiz.Question("prompt", List.of("A", "B", "C"), answer);
    }

    /** A quiz whose i-th correct answer is the i-th of {@code answers}. */
    private static Quiz quizWithKey(String... key) {
        return new Quiz(Arrays.stream(key).map(QuizGraderTest::question).toList());
    }

    private static TaskTypeDefinition quizType(int expBase, boolean scaleByScore) {
        return TaskTypeDefinition.builder()
                .key("QUIZ").displayName("Quiz")
                .verificationMethod(VerificationMethod.AUTO_QUIZ)
                .expBase(expBase).expScaleByScore(scaleByScore).requiresArtifact(false)
                .build();
    }

    @Test
    void grade_shouldScore100AndAwardFullExp_whenAllCorrect() {
        // Arrange — 2/2 correct, exp scaled, single skill, expBase 40
        Quiz quiz = quizWithKey("A", "B");
        List<String> answers = List.of("A", "B");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(40, true), List.of("JAVA"));

        // Assert — perfect score, passed, full expBase awarded
        assertThat(out.score()).isEqualTo(100);
        assertThat(out.passed()).isTrue();
        assertThat(out.expProposed()).isEqualTo(40L);
        assertThat(out.skillBreakdown()).singleElement().satisfies(b -> {
            assertThat(b.skillKey()).isEqualTo("JAVA");
            assertThat(b.exp()).isEqualTo(40L);
        });
    }

    @Test
    void grade_shouldScore0AndAwardNoExp_whenAllWrong() {
        // Arrange — 0/2 correct
        Quiz quiz = quizWithKey("A", "B");
        List<String> answers = List.of("C", "C");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(40, true), List.of("JAVA"));

        // Assert — floor: zero score, failed, zero exp
        assertThat(out.score()).isZero();
        assertThat(out.passed()).isFalse();
        assertThat(out.expProposed()).isZero();
        assertThat(out.skillBreakdown()).singleElement()
                .satisfies(b -> assertThat(b.exp()).isZero());
    }

    @Test
    void grade_shouldScaleExpByPercent_whenPartiallyCorrectAndPassing() {
        // Arrange — 4/5 = 80%, scaled, expBase 50
        Quiz quiz = quizWithKey("A", "A", "A", "A", "A");
        List<String> answers = List.of("A", "A", "A", "A", "B");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(50, true), List.of("JAVA"));

        // Assert — score 80, passed, exp = round(50 * 0.8) = 40
        assertThat(out.score()).isEqualTo(80);
        assertThat(out.passed()).isTrue();
        assertThat(out.expProposed()).isEqualTo(40L);
    }

    @Test
    void grade_shouldPass_atThresholdBoundary() {
        // Arrange — 3/5 = 60% == PASS_SCORE
        Quiz quiz = quizWithKey("A", "A", "A", "A", "A");
        List<String> answers = List.of("A", "A", "A", "B", "B");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(50, true), List.of("JAVA"));

        // Assert — exactly the threshold passes; exp = round(50 * 0.6) = 30
        assertThat(out.score()).isEqualTo(60);
        assertThat(out.passed()).isTrue();
        assertThat(out.expProposed()).isEqualTo(30L);
    }

    @Test
    void grade_shouldFailAndAwardNoExp_belowThreshold() {
        // Arrange — 2/5 = 40% < PASS_SCORE (partial correctness, still a reject)
        Quiz quiz = quizWithKey("A", "A", "A", "A", "A");
        List<String> answers = List.of("A", "A", "B", "B", "B");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(50, true), List.of("JAVA"));

        // Assert — a failed quiz earns nothing even though 40% were correct
        assertThat(out.score()).isEqualTo(40);
        assertThat(out.passed()).isFalse();
        assertThat(out.expProposed()).isZero();
    }

    @Test
    void grade_shouldAwardFullExpWithoutScaling_whenExpScaleByScoreFalse() {
        // Arrange — 3/4 = 75% passing, but the type does NOT scale by score
        Quiz quiz = quizWithKey("A", "A", "A", "A");
        List<String> answers = List.of("A", "A", "A", "B");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(50, false), List.of("JAVA"));

        // Assert — passing earns the full expBase, unscaled
        assertThat(out.score()).isEqualTo(75);
        assertThat(out.passed()).isTrue();
        assertThat(out.expProposed()).isEqualTo(50L);
    }

    @Test
    void grade_shouldDistributeExpAcrossAllSkills() {
        // Arrange — perfect score, two skills
        Quiz quiz = quizWithKey("A", "A");
        List<String> answers = List.of("A", "A");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(30, true), List.of("JAVA", "TESTING"));

        // Assert — each skill gets the scaled exp; total is the sum
        assertThat(out.skillBreakdown()).extracting(SkillBreakdown::skillKey)
                .containsExactlyInAnyOrder("JAVA", "TESTING");
        assertThat(out.skillBreakdown()).allSatisfy(b -> assertThat(b.exp()).isEqualTo(30L));
        assertThat(out.expProposed()).isEqualTo(60L);
    }

    @Test
    void grade_shouldMatchAnswersCaseInsensitivelyAndTrimmed() {
        // Arrange — the key is "Paris"; the user typed padded, lower-case
        Quiz quiz = new Quiz(List.of(new Quiz.Question("Capital of France?", List.of("Paris", "Rome"), "Paris")));
        List<String> answers = List.of("  paris ");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(20, true), List.of("GEO"));

        // Assert — normalised comparison counts it correct
        assertThat(out.score()).isEqualTo(100);
        assertThat(out.passed()).isTrue();
    }

    @Test
    void grade_shouldScoreZeroAndFail_whenQuizHasNoQuestions() {
        // Arrange — a degenerate (empty) quiz; no division-by-zero
        Quiz quiz = new Quiz(List.of());

        // Act
        EvaluationOutput out = grader.grade(quiz, List.of(), quizType(40, true), List.of("JAVA"));

        // Assert — zero score, failed, zero exp
        assertThat(out.score()).isZero();
        assertThat(out.passed()).isFalse();
        assertThat(out.expProposed()).isZero();
        assertThat(out.skillBreakdown()).singleElement()
                .satisfies(b -> assertThat(b.exp()).isZero());
    }

    @Test
    void grade_shouldScoreZeroAndFail_whenQuizIsNull() {
        // Arrange / Act — a missing quiz must not throw
        EvaluationOutput out = grader.grade(null, List.of("A"), quizType(40, true), List.of("JAVA"));

        // Assert
        assertThat(out.score()).isZero();
        assertThat(out.passed()).isFalse();
        assertThat(out.expProposed()).isZero();
    }

    @Test
    void grade_shouldCountMissingAnswersAsWrong_whenFewerAnswersThanQuestions() {
        // Arrange — 2 questions, only 1 answer supplied (both keys "A")
        Quiz quiz = quizWithKey("A", "A");
        List<String> answers = List.of("A");

        // Act
        EvaluationOutput out = grader.grade(quiz, answers, quizType(40, true), List.of("JAVA"));

        // Assert — the unanswered question is wrong → 1/2 = 50% < threshold → fail
        assertThat(out.score()).isEqualTo(50);
        assertThat(out.passed()).isFalse();
        assertThat(out.expProposed()).isZero();
    }
}
