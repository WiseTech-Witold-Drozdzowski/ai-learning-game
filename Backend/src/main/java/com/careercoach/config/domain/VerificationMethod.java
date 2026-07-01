package com.careercoach.config.domain;

/**
 * Verification strategy for a task type. Only {@link #HONOR} / {@link #HONOR_WITH_PROOF}
 * are functionally used in PRD-1; the rest are reserved for later modules.
 */
public enum VerificationMethod {
    HONOR,
    HONOR_WITH_PROOF,
    AUTO_QUIZ,
    AI_DIALOG,
    AI_ARTIFACT_REVIEW
}
