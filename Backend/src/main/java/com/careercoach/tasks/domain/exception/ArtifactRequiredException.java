package com.careercoach.tasks.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class ArtifactRequiredException extends RuntimeException {

    public ArtifactRequiredException(String message) {
        super(message);
    }

    public ArtifactRequiredException(Long taskId) {
        this("Task " + taskId + " requires an artifact to be submitted");
    }
}
