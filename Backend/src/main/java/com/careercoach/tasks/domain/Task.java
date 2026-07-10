package com.careercoach.tasks.domain;

import java.time.Instant;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "goal_id", nullable = false)
    private Long goalId;

    @Column(name = "type_key", nullable = false)
    private String typeKey;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TaskState state;

    @Builder.Default
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "skill_keys", columnDefinition = "text[]")
    private List<String> skillKeys = new java.util.ArrayList<>();

    private String artifact;

    /**
     * AI-generated quiz + answer key for AUTO_QUIZ tasks (issue-5). {@code @JsonIgnore}d
     * so the answer key never leaks through {@code GET /tasks/{id}} — the client sees
     * questions via the {@code QuizView} projection returned by quiz generation.
     */
    @JsonIgnore
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "quiz", columnDefinition = "jsonb")
    private Quiz quiz;

    @Column(name = "exp_awarded", nullable = false)
    private long expAwarded;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    @Column(name = "verification_job_id")
    private Long verificationJobId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
