package com.careercoach.jobs;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Job entity — the universal asynchronous work unit (BACKEND_DESIGN §2.7 / §4).
 * State lives entirely in the DB (restart-safe). Typed {@code input}/{@code output}
 * payloads are stored as JSONB via native Hibernate 6 mapping.
 */
@Entity
@Table(name = "job")
@Getter
@Setter
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private JobStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JobPayload input;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JobPayload output;

    @Column(name = "related_goal_id")
    private Long relatedGoalId;

    @Column(name = "related_task_id")
    private Long relatedTaskId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts = 3;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column
    private String error;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected Job() {
        // for JPA
    }

    public Job(JobType type, JobStatus status) {
        this.type = type;
        this.status = status;
    }

    /** Factory for a QUEUED job with {@code attempts=0}. */
    public static Job queued(JobType type, JobPayload input, Long relatedGoalId,
                             Long relatedTaskId, int maxAttempts) {
        Job job = new Job(type, JobStatus.QUEUED);
        job.input = input;
        job.relatedGoalId = relatedGoalId;
        job.relatedTaskId = relatedTaskId;
        job.attempts = 0;
        job.maxAttempts = maxAttempts;
        return job;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
