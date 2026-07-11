package com.careercoach.coach.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.careercoach.goals.domain.GoalState;

/**
 * Rejected planning request — e.g. GENERATE_TASKS asked for a goal that is not
 * {@code ACTIVE} (the coach may only generate tasks under an active goal).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalPlanningStateException extends RuntimeException {

    public IllegalPlanningStateException(String message) {
        super(message);
    }

    public IllegalPlanningStateException(Long goalId, GoalState state) {
        super("Cannot GENERATE_TASKS for goal " + goalId + " in state " + state + " (must be ACTIVE)");
    }
}
