package com.careercoach.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.config.repository.TaskTypeDefinitionRepository;
import com.careercoach.config.seed.ConfigSeeder;
import com.careercoach.config.service.TaskTypeDefinitionService;
import com.careercoach.config.web.TaskTypeUpsertRequest;

/**
 * Verifies the seeder's insert-if-absent idempotency (issue-2) against a real
 * Postgres (provided by {@code run-tests.sh}). The seeder already ran once on
 * {@code ApplicationReadyEvent} before this test class starts; here it is
 * invoked directly to prove a second run is safe.
 */
@SpringBootTest
class ConfigSeederIntegrationTest {

    @Autowired
    private ConfigSeeder configSeeder;

    @Autowired
    private TaskTypeDefinitionService taskTypeDefinitionService;

    @Autowired
    private TaskTypeDefinitionRepository taskTypeDefinitionRepository;

    @Autowired
    private SkillDefinitionRepository skillDefinitionRepository;

    @Test
    void reseedingShouldNotDuplicateRows() {
        // Arrange
        long taskTypesBefore = taskTypeDefinitionRepository.count();
        long skillsBefore = skillDefinitionRepository.count();

        // Act — run the seed logic a second time.
        configSeeder.seed();

        // Assert
        assertThat(taskTypeDefinitionRepository.count()).isEqualTo(taskTypesBefore);
        assertThat(skillDefinitionRepository.count()).isEqualTo(skillsBefore);
    }

    @Test
    void reseedingShouldNotOverwriteRuntimeEditedEntry() {
        // Arrange — edit a seeded entry at runtime.
        taskTypeDefinitionService.upsert("HONOR_CHECK",
                new TaskTypeUpsertRequest("Runtime edited name", VerificationMethod.HONOR_WITH_PROOF, 77, true, true));

        // Act — reseed.
        configSeeder.seed();

        // Assert — the runtime edit survives; the seed default was not reapplied.
        TaskTypeDefinition reloaded = taskTypeDefinitionRepository.findById("HONOR_CHECK").orElseThrow();
        assertThat(reloaded.getDisplayName()).isEqualTo("Runtime edited name");
        assertThat(reloaded.getExpBase()).isEqualTo(77);
    }

    @Test
    void reseedingShouldReinsertManuallyDeletedSeedKey_andLeaveOthersUntouched() {
        // Arrange — a seeded key is deleted, another seeded key is left as-is.
        taskTypeDefinitionRepository.deleteById("HONOR_WITH_PROOF");
        assertThat(taskTypeDefinitionRepository.findById("HONOR_WITH_PROOF")).isEmpty();
        TaskTypeDefinition untouchedBefore = taskTypeDefinitionRepository.findById("HONOR_CHECK").orElse(null);

        // Act — reseed.
        configSeeder.seed();

        // Assert — the deleted key is restored from the YAML default...
        assertThat(taskTypeDefinitionRepository.findById("HONOR_WITH_PROOF")).isPresent();
        // ...and the untouched key is left as-is if it existed before this run.
        if (untouchedBefore != null) {
            TaskTypeDefinition afterReseed = taskTypeDefinitionRepository.findById("HONOR_CHECK").orElseThrow();
            assertThat(afterReseed.getDisplayName()).isEqualTo(untouchedBefore.getDisplayName());
            assertThat(afterReseed.getExpBase()).isEqualTo(untouchedBefore.getExpBase());
        }
    }
}
