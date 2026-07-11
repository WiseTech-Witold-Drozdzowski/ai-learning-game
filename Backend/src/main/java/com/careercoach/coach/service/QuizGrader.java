package com.careercoach.coach.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.tasks.domain.Quiz;
import com.careercoach.coach.domain.EvaluationOutput;
import com.careercoach.coach.domain.SkillBreakdown;

/**
 * Deterministic grader for AUTO_QUIZ evaluations (issue-5). Scores the user's
 * {@code answers} against the stored answer key — <em>no AI call</em> — and proposes
 * exp scaled by the fraction correct when the task type has {@code expScaleByScore}.
 * The AI only <em>generated</em> the quiz; grading and the exp proposal are pure code.
 */
@Service
public class QuizGrader {

    /** Minimum {@code score} (percent correct) required to pass a quiz. */
    static final int PASS_SCORE = 60;

    /**
     * Grade {@code answers} against {@code quiz}'s answer key, distributing the
     * score-scaled exp across {@code skillKeys}. Returns the same
     * {@link EvaluationOutput} shape as the artifact-review path so the handler can
     * award uniformly.
     */
    public EvaluationOutput grade(Quiz quiz, List<String> answers, TaskTypeDefinition type,
                                  List<String> skillKeys) {
        List<Quiz.Question> questions = quiz == null || quiz.questions() == null ? List.of() : quiz.questions();
        int total = questions.size();

        int correct = 0;
        for (int i = 0; i < total; i++) {
            String expected = questions.get(i).answer();
            String actual = answers != null && i < answers.size() ? answers.get(i) : null;
            if (isCorrect(expected, actual)) {
                correct++;
            }
        }

        double percent = total == 0 ? 0.0 : (double) correct / total;
        int score = (int) Math.round(percent * 100);
        boolean passed = score >= PASS_SCORE;

        // A failed quiz earns nothing (REJECTED = "practice more", BACKEND_DESIGN §2.3); a passing
        // one earns exp scaled by the fraction correct, or the flat expBase when scaling is off.
        int expPerSkill;
        if (!passed) {
            expPerSkill = 0;
        } else if (type.isExpScaleByScore()) {
            expPerSkill = (int) Math.round(type.getExpBase() * percent);
        } else {
            expPerSkill = type.getExpBase();
        }

        List<String> keys = skillKeys == null ? List.of() : skillKeys;
        List<SkillBreakdown> breakdown = keys.stream()
                .map(key -> new SkillBreakdown(key, expPerSkill))
                .toList();
        long expProposed = (long) expPerSkill * breakdown.size();

        String feedback = String.format("Answered %d of %d correctly (%d%%).", correct, total, score);
        return new EvaluationOutput(score, expProposed, breakdown, feedback, passed);
    }

    private static boolean isCorrect(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.trim().equalsIgnoreCase(actual.trim());
    }
}
