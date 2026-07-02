package com.careercoach.goals.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.careercoach.goals.domain.GoalState;

@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalGoalStateTransitionException extends RuntimeException {

    public IllegalGoalStateTransitionException(String message) {
        super(message);
    }

    public IllegalGoalStateTransitionException(Long id, String action, GoalState state) {
        this("Cannot " + action + " goal " + id + " in state " + state);
    }
}
