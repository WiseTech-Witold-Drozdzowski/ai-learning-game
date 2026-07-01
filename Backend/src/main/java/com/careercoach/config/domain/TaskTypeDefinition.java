package com.careercoach.config.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_type_definition")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskTypeDefinition {

    @Id
    private String key;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", nullable = false, length = 32)
    private VerificationMethod verificationMethod;

    @Column(name = "exp_base", nullable = false)
    private int expBase;

    @Column(name = "exp_scale_by_score", nullable = false)
    private boolean expScaleByScore;

    @Column(name = "requires_artifact", nullable = false)
    private boolean requiresArtifact;
}
