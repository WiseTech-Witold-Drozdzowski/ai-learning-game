package com.careercoach.coach;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Beans for the coach chat: a small async executor so streaming the LLM reply does not
 * block the servlet thread (tests override it with a synchronous executor).
 */
@Configuration
public class CoachChatConfig {

    @Bean("coachChatExecutor")
    public Executor coachChatExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(16);
        executor.setThreadNamePrefix("coach-chat-");
        executor.initialize();
        return executor;
    }
}
