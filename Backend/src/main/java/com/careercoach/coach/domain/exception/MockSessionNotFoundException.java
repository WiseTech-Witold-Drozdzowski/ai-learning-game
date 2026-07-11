package com.careercoach.coach.domain.exception;


/** Thrown when a mock session id does not resolve to a persisted {@link MockSession}. */
public class MockSessionNotFoundException extends RuntimeException {

    public MockSessionNotFoundException(Long sessionId) {
        super("Mock session not found: " + sessionId);
    }
}
