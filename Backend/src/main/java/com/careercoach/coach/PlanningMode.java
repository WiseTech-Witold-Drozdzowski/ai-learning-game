package com.careercoach.coach;

/**
 * What a PLANNING job should produce (BACKEND_DESIGN §4).
 *
 * <ul>
 *   <li>{@link #DECOMPOSE} — break a goal into sub-goals (proposed goals).</li>
 *   <li>{@link #GENERATE_TASKS} — generate concrete tasks under an ACTIVE goal.</li>
 * </ul>
 */
public enum PlanningMode {
    DECOMPOSE,
    GENERATE_TASKS
}
