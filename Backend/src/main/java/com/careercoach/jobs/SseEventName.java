package com.careercoach.jobs;

/** Named channels on the global SSE stream ({@code GET /api/events}). */
public enum SseEventName {

    JOB_STATUS("job-status"),
    EXP_GAIN("exp-gain"),
    LEVEL_UP("level-up");

    private final String wireName;

    SseEventName(String wireName) {
        this.wireName = wireName;
    }

    /** The event name as it appears on the wire (the SSE {@code event:} field). */
    public String wireName() {
        return wireName;
    }
}
