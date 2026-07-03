package com.careercoach.tasks.domain;

/**
 * Task lifecycle state. TODO -&gt; IN_PROGRESS -&gt; DONE, or TODO/IN_PROGRESS -&gt; REJECTED.
 */
public enum TaskState {
    TODO,
    IN_PROGRESS,
    DONE,
    REJECTED
}
