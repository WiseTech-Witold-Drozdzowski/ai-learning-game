package com.careercoach.coach.web;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careercoach.coach.IllegalPlanningStateException;
import com.careercoach.coach.PlanningMode;
import com.careercoach.coach.PlanningService;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;

/**
 * Web-slice test for {@code POST /api/goals/{id}/plan} (issue-2) — the plan
 * endpoint returns the created PLANNING job's id/status. Red phase: the service
 * is mocked but the real controller delegates to a not-yet-implemented service.
 */
@WebMvcTest(PlanController.class)
class PlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlanningService planningService;

    private static Job job(Long id, JobStatus status) {
        Job job = new Job(JobType.PLANNING, status);
        job.setId(id);
        return job;
    }

    @Test
    @WithMockUser
    void plan_shouldReturnJobId_whenModeValid() throws Exception {
        // Arrange
        when(planningService.plan(eq(7L), eq(PlanningMode.DECOMPOSE)))
                .thenReturn(job(55L, JobStatus.QUEUED));

        // Act / Assert
        mockMvc.perform(post("/api/goals/7/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"DECOMPOSE\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.type").value("PLANNING"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    @WithMockUser
    void plan_shouldReturnBadRequest_whenModeMissing() throws Exception {
        // Act / Assert — @NotNull mode
        mockMvc.perform(post("/api/goals/7/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void plan_shouldReturnConflict_whenServiceRejectsState() throws Exception {
        // Arrange
        when(planningService.plan(eq(7L), eq(PlanningMode.GENERATE_TASKS)))
                .thenThrow(new IllegalPlanningStateException("not active"));

        // Act / Assert
        mockMvc.perform(post("/api/goals/7/plan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"GENERATE_TASKS\"}"))
                .andExpect(status().isConflict());
    }
}
