package com.careercoach.jobs;

import java.time.Clock;
import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Beans for the job runner: the async executor and an injectable Clock. */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(JobRunnerProperties.class)
public class JobConfig {

    @Bean("jobExecutor")
    public Executor jobExecutor(JobRunnerProperties props) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(props.getDefaultConcurrency());
        executor.setMaxPoolSize(Math.max(props.getDefaultConcurrency(), props.getBatchSize()));
        executor.setQueueCapacity(props.getBatchSize());
        executor.setThreadNamePrefix("job-");
        executor.initialize();
        return executor;
    }

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
