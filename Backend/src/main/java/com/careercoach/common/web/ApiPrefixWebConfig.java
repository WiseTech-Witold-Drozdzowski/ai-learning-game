package com.careercoach.common.web;

import java.util.Set;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.careercoach.auth.web.MeController;
import com.careercoach.coach.web.CoachController;
import com.careercoach.coach.web.PlanController;
import com.careercoach.common.PingController;
import com.careercoach.config.web.SkillDefController;
import com.careercoach.config.web.TaskTypeController;
import com.careercoach.gamification.web.ProfileController;
import com.careercoach.gamification.web.SkillController;
import com.careercoach.goals.web.GoalController;
import com.careercoach.jobs.web.EventController;
import com.careercoach.jobs.web.JobController;
import com.careercoach.tasks.web.TaskController;

/**
 * Applies the {@code /api} prefix centrally to controllers that map their
 * routes relative to their resource (e.g. {@code /profile}), instead of each
 * controller hard-coding the prefix.
 */
@Configuration
public class ApiPrefixWebConfig implements WebMvcConfigurer {

    private static final Set<Class<?>> API_CONTROLLERS = Set.of(
            TaskController.class, ProfileController.class, SkillController.class,
            GoalController.class, SkillDefController.class, TaskTypeController.class,
            MeController.class, PingController.class,
            JobController.class, EventController.class, PlanController.class,
            CoachController.class);

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api", API_CONTROLLERS::contains);
    }
}
