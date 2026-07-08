package com.careercoach.jobs;

import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Marker supertype for all typed Job input/output payloads. The {@code jobs}
 * module stays agnostic of concrete shapes; Jackson embeds the concrete class
 * name in the JSON so any module's record round-trips through the single JSONB
 * column without central registration.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public interface JobPayload {
}
