package com.careercoach;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point — modular monolith (see TECHNICAL_DESIGN §1).
 *
 * <p>Modules live as packages: {@code goals, tasks, jobs, coach, ai,
 * gamification, calendar, auth, config}. {@code @EnableScheduling} is here for
 * the future Job poller (TECHNICAL_DESIGN §2 / BACKEND_DESIGN §4).
 */
@SpringBootApplication
@EnableScheduling
public class CareerCoachApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerCoachApplication.class, args);
    }
}
