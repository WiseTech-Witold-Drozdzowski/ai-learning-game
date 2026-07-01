package com.careercoach.config.seed;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.seed.ConfigSeedProperties.SkillSeed;
import com.careercoach.config.seed.ConfigSeedProperties.TaskTypeSeed;
import com.careercoach.config.service.SkillDefinitionService;
import com.careercoach.config.service.TaskTypeDefinitionService;

import lombok.extern.slf4j.Slf4j;

/**
 * Loads the default configuration catalog (task types + skill definitions) from the
 * YAML-bound {@link ConfigSeedProperties} into the database once the application is
 * ready. Seeding is idempotent (insert-if-absent by key): the DB is the source of
 * truth, so a restart never duplicates rows nor overwrites entries edited at runtime.
 */
@Slf4j
@Component
public class ConfigSeeder {

    private final ConfigSeedProperties props;
    private final TaskTypeDefinitionService taskTypes;
    private final SkillDefinitionService skills;

    public ConfigSeeder(ConfigSeedProperties props, TaskTypeDefinitionService taskTypes,
                         SkillDefinitionService skills) {
        this.props = props;
        this.taskTypes = taskTypes;
        this.skills = skills;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        int taskTypesInserted = 0;
        int taskTypesSkipped = 0;
        List<TaskTypeSeed> taskTypeSeeds = props.getTaskTypes();
        if (taskTypeSeeds != null) {
            for (TaskTypeSeed taskTypeSeed : taskTypeSeeds) {
                TaskTypeDefinition def = new TaskTypeDefinition(taskTypeSeed.key(), taskTypeSeed.displayName(),
                        taskTypeSeed.verificationMethod(), taskTypeSeed.expBase(), taskTypeSeed.expScaleByScore(),
                        taskTypeSeed.requiresArtifact());
                if (taskTypes.seedIfAbsent(def)) {
                    taskTypesInserted++;
                } else {
                    taskTypesSkipped++;
                }
            }
        }

        int skillsInserted = 0;
        int skillsSkipped = 0;
        List<SkillSeed> skillSeeds = props.getSkills();
        if (skillSeeds != null) {
            for (SkillSeed skillSeed : skillSeeds) {
                SkillDefinition def = new SkillDefinition(skillSeed.key(), skillSeed.displayName(),
                        skillSeed.category());
                if (skills.seedIfAbsent(def)) {
                    skillsInserted++;
                } else {
                    skillsSkipped++;
                }
            }
        }

        log.info("Config seed complete: task-types inserted={} skipped={}, skills inserted={} skipped={}",
                taskTypesInserted, taskTypesSkipped, skillsInserted, skillsSkipped);
    }
}
