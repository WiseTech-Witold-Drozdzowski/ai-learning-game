package com.careercoach.coach.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careercoach.coach.QuizGenerationService;
import com.careercoach.tasks.domain.Quiz;

/**
 * Web-slice test for {@code POST /api/tasks/{id}/quiz} (issue-5): the generated quiz
 * is returned to the client with prompts + options but with the answer key stripped
 * — the response JSON must not contain any {@code answer} field.
 */
@WebMvcTest(QuizController.class)
class QuizControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QuizGenerationService quizGenerationService;

    @Test
    @WithMockUser
    void generateQuiz_shouldReturnQuestionsWithoutAnswerKey() throws Exception {
        // Arrange — the service produces a quiz whose questions carry the correct answers
        when(quizGenerationService.generate(7L)).thenReturn(new Quiz(List.of(
                new Quiz.Question("Capital of France?", List.of("Paris", "Rome"), "Paris"))));

        // Act / Assert — prompt + options are exposed; the answer key is absent
        mockMvc.perform(post("/api/tasks/7/quiz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].prompt").value("Capital of France?"))
                .andExpect(jsonPath("$.questions[0].options[0]").value("Paris"))
                .andExpect(jsonPath("$.questions[0].answer").doesNotExist());
    }
}
