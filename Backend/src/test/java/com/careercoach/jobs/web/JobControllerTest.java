package com.careercoach.jobs.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.careercoach.jobs.EchoPayload;
import com.careercoach.jobs.Job;
import com.careercoach.jobs.JobNotFoundException;
import com.careercoach.jobs.JobService;
import com.careercoach.jobs.JobStatus;
import com.careercoach.jobs.JobType;

/** Web-slice test for {@code /api/jobs/{id}} (issue-1) — controller with the service mocked. */
@WebMvcTest(JobController.class)
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobService jobService;

    private static Job job(JobStatus status) {
        Job job = Job.queued(JobType.ECHO, new EchoPayload("hi"), null, null, 3);
        job.setStatus(status);
        job.setOutput(new EchoPayload("echo: hi"));
        return job;
    }

    @Test
    @WithMockUser
    void get_shouldReturnJobStatus() throws Exception {
        // Arrange
        when(jobService.get(1L)).thenReturn(job(JobStatus.DONE));

        // Act / Assert
        mockMvc.perform(get("/api/jobs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("ECHO"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.attempts").value(0));
    }

    @Test
    @WithMockUser
    void get_shouldReturnNotFound_whenJobMissing() throws Exception {
        // Arrange
        when(jobService.get(99L)).thenThrow(new JobNotFoundException(99L));

        // Act / Assert
        mockMvc.perform(get("/api/jobs/99"))
                .andExpect(status().isNotFound());
    }
}
