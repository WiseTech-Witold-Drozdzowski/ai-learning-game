package com.careercoach.jobs;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Poller/runner tuning (BACKEND_DESIGN §4 resilience knobs). */
@ConfigurationProperties(prefix = "careercoach.jobs")
@Getter
@Setter
public class JobRunnerProperties {

    private long pollDelayMs = 1000;
    private int batchSize = 10;
    private int defaultConcurrency = 4;
    private Map<JobType, Integer> concurrency = defaultConcurrencyMap();
    private long runningTimeoutSeconds = 60;
    private int defaultMaxAttempts = 3;
    private long backoffBaseSeconds = 2;
    private boolean schedulerEnabled = true;

    private static Map<JobType, Integer> defaultConcurrencyMap() {
        Map<JobType, Integer> map = new EnumMap<>(JobType.class);
        map.put(JobType.AGENT, 1);
        return map;
    }

    /** Concurrency limit for a type, falling back to {@link #defaultConcurrency}. */
    public int limitFor(JobType type) {
        return concurrency.getOrDefault(type, defaultConcurrency);
    }
}
