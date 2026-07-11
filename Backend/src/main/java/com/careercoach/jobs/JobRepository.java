package com.careercoach.jobs;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Job repository — claim/recovery/count queries for the runner. */
public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * Claim candidate ids for a type: QUEUED and due ({@code next_run_at} null or
     * &lt;= now), FIFO by creation, row-locked with {@code FOR UPDATE SKIP LOCKED}
     * so concurrent pollers don't fight over the same rows.
     */
    @Query(value = """
            SELECT id FROM job
            WHERE type = :type
              AND status = 'QUEUED'
              AND (next_run_at IS NULL OR next_run_at <= :now)
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<Long> findClaimableIds(@Param("type") String type,
                                @Param("limit") int limit,
                                @Param("now") Instant now);

    /** Running-count per type, for the concurrency limit. */
    long countByTypeAndStatus(JobType type, JobStatus status);

    /** Stuck jobs in the given status whose {@code lockedAt} predates the threshold. */
    List<Job> findByStatusAndLockedAtBefore(JobStatus status, Instant threshold);
}
