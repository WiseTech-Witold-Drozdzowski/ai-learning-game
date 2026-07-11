package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link JobService} (issue-1) — enqueue and lookup. */
@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    private JobRunnerProperties props;
    private JobService service;

    @BeforeEach
    void setUp() {
        props = new JobRunnerProperties();
        service = new JobService(jobRepository, props);
    }

    @Test
    void enqueue_shouldPersistQueuedJob_withDefaultMaxAttempts() {
        // Arrange
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        when(jobRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.enqueue(JobType.ECHO, new EchoPayload("hi"), 5L, 7L);

        // Assert
        Job saved = captor.getValue();
        assertThat(saved.getType()).isEqualTo(JobType.ECHO);
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getMaxAttempts()).isEqualTo(props.getDefaultMaxAttempts());
        assertThat(saved.getRelatedGoalId()).isEqualTo(5L);
        assertThat(saved.getRelatedTaskId()).isEqualTo(7L);
        assertThat(saved.getInput()).isEqualTo(new EchoPayload("hi"));
    }

    @Test
    void enqueue_shouldSucceed_whenRelatedGoalAndTaskIdsAreNull() {
        // Arrange
        ArgumentCaptor<Job> captor = ArgumentCaptor.forClass(Job.class);
        when(jobRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        service.enqueue(JobType.ECHO, new EchoPayload("hi"), null, null);

        // Assert
        Job saved = captor.getValue();
        assertThat(saved.getRelatedGoalId()).isNull();
        assertThat(saved.getRelatedTaskId()).isNull();
        assertThat(saved.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void get_shouldReturnJob_whenPresent() {
        // Arrange
        Job job = new Job(JobType.ECHO, JobStatus.DONE);
        when(jobRepository.findById(1L)).thenReturn(Optional.of(job));

        // Act / Assert
        assertThat(service.get(1L)).isSameAs(job);
    }

    @Test
    void get_shouldThrowJobNotFoundException_whenIdUnknown() {
        // Arrange
        when(jobRepository.findById(99L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.get(99L)).isInstanceOf(JobNotFoundException.class);
    }
}
