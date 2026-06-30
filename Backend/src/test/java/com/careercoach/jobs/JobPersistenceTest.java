package com.careercoach.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * TEMPORARY integration test confirming the full persistence path:
 * full Spring context → Flyway (V1) → JPA/Hibernate → PostgreSQL.
 *
 * <p>Requires a running database — the datasource comes from env
 * (SPRING_DATASOURCE_*). Run via {@code run-tests.sh}, which provides
 * Postgres in Docker.
 */
@SpringBootTest
class JobPersistenceTest {

    @Autowired
    private JobRepository jobRepository;

    @Test
    void savesAndReadsJob() {
        Job saved = jobRepository.save(new Job(JobType.EVALUATION, JobStatus.QUEUED));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();

        Job found = jobRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getType()).isEqualTo(JobType.EVALUATION);
        assertThat(found.getStatus()).isEqualTo(JobStatus.QUEUED);
    }

    @Test
    void countReflectsInsertedRows() {
        long before = jobRepository.count();

        jobRepository.saveAll(List.of(
                new Job(JobType.PLANNING, JobStatus.QUEUED),
                new Job(JobType.AGENT, JobStatus.QUEUED)));

        assertThat(jobRepository.count()).isEqualTo(before + 2);
    }
}
