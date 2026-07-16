package com.careercoach.coach.domain.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Thrown when a coach-note id does not resolve to a persisted note. */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class CoachNoteNotFoundException extends RuntimeException {

    public CoachNoteNotFoundException(Long id) {
        super("Coach note not found: " + id);
    }
}
