package com.careercoach.jobs;

/** Job types (TECHNICAL_DESIGN §2, BACKEND_DESIGN §4). */
public enum JobType {
    PLANNING,
    EVALUATION,
    AGENT,
    /** Tracer/no-op type driving the full lifecycle end-to-end. */
    ECHO
}
