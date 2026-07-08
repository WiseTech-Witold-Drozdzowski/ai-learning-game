package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Unit tests for {@link JobHandlerRegistry} (issue-1) — strategy dispatch by {@link JobType}. */
class JobHandlerRegistryTest {

    private static JobHandler<JobPayload> handlerFor(JobType type) {
        return new JobHandler<>() {
            @Override
            public JobType type() {
                return type;
            }

            @Override
            public Class<JobPayload> inputType() {
                return JobPayload.class;
            }

            @Override
            public JobResult handle(Job job, JobPayload input) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void handlerFor_shouldReturnMatchingHandler_whenMultipleTypesRegistered() {
        // Arrange
        JobHandler<?> echoHandler = handlerFor(JobType.ECHO);
        JobHandler<?> planningHandler = handlerFor(JobType.PLANNING);
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(echoHandler, planningHandler));

        // Act / Assert
        assertThat(registry.handlerFor(JobType.ECHO)).isSameAs(echoHandler);
        assertThat(registry.handlerFor(JobType.PLANNING)).isSameAs(planningHandler);
    }

    @Test
    void handlerFor_shouldThrow_whenNoHandlerRegisteredForType() {
        // Arrange
        JobHandlerRegistry registry = new JobHandlerRegistry(List.of(handlerFor(JobType.ECHO)));

        // Act / Assert
        assertThatThrownBy(() -> registry.handlerFor(JobType.AGENT))
                .isInstanceOf(JobHandlerNotFoundException.class);
    }

    @Test
    void constructor_shouldThrowIllegalStateException_whenDuplicateTypeRegistered() {
        // Arrange
        JobHandler<?> first = handlerFor(JobType.ECHO);
        JobHandler<?> second = handlerFor(JobType.ECHO);

        // Act / Assert
        assertThatThrownBy(() -> new JobHandlerRegistry(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class);
    }
}
