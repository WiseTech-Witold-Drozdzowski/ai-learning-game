package com.careercoach.coach.domain;

/** Lifecycle of a {@link MockSession}: {@code ACTIVE} while the interview runs, {@code FINISHED} once evaluated. */
public enum MockSessionState {
    ACTIVE,
    FINISHED
}
