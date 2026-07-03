package com.careercoach.tasks.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.careercoach.tasks.domain.TaskState;

@ResponseStatus(HttpStatus.CONFLICT)
public class IllegalTaskStateTransitionException extends RuntimeException {

    public IllegalTaskStateTransitionException(String message) {
        super(message);
    }

    public IllegalTaskStateTransitionException(Long id, String action, TaskState state) {
        this("Cannot " + action + " task " + id + " in state " + state);
    }
}
