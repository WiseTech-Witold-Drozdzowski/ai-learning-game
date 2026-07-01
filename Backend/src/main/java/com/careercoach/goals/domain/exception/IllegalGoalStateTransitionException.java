package com.careercoach.goals.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalGoalStateTransitionException extends RuntimeException {

    public IllegalGoalStateTransitionException(String message) {
        super(message);
    }
}
