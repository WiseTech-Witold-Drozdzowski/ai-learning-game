package com.careercoach.config.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import com.careercoach.config.domain.SkillDefinition;
import com.careercoach.config.repository.SkillDefinitionRepository;
import com.careercoach.config.web.SkillUpsertRequest;

/**
 * Unit tests for {@link SkillDefinitionService} (issue-2) — repository is
 * mocked. Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class SkillDefinitionServiceTest {

    @Mock
    private SkillDefinitionRepository repository;

    private SkillDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new SkillDefinitionService(repository);
    }

    @Test
    void shouldReturnAllEntriesOrderedByKey_whenListed() {
        // Arrange — repository returns entries out of order; the service must sort by key.
        SkillDefinition zKey = new SkillDefinition("Z_KEY", "Z", "TECHNICAL");
        SkillDefinition aKey = new SkillDefinition("A_KEY", "A", "TECHNICAL");
        lenient().when(repository.findAll()).thenReturn(List.of(zKey, aKey));
        lenient().when(repository.findAll(any(Sort.class))).thenReturn(List.of(aKey, zKey));

        // Act
        List<SkillDefinition> result = service.list();

        // Assert
        assertThat(result).extracting(SkillDefinition::getKey).containsExactly("A_KEY", "Z_KEY");
    }

    @Test
    void shouldReturnEntry_whenPresent() {
        // Arrange
        SkillDefinition def = new SkillDefinition("JAVA", "Java", "TECHNICAL");
        when(repository.findById("JAVA")).thenReturn(Optional.of(def));

        // Act
        SkillDefinition result = service.get("JAVA");

        // Assert
        assertThat(result).isSameAs(def);
    }

    @Test
    void shouldThrowConfigEntryNotFoundException_whenAbsent() {
        // Arrange
        when(repository.findById("MISSING")).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> service.get("MISSING"))
                .isInstanceOf(ConfigEntryNotFoundException.class);
    }

    @Test
    void shouldCreateNewEntity_whenUpsertingAbsentKey() {
        // Arrange
        when(repository.findById("NEW_SKILL")).thenReturn(Optional.empty());
        ArgumentCaptor<SkillDefinition> captor = ArgumentCaptor.forClass(SkillDefinition.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());
        SkillUpsertRequest req = new SkillUpsertRequest("New Skill", "SOFT");

        // Act
        SkillDefinition result = service.upsert("NEW_SKILL", req);

        // Assert
        assertThat(result.getKey()).isEqualTo("NEW_SKILL");
        assertThat(result.getDisplayName()).isEqualTo("New Skill");
        assertThat(result.getCategory()).isEqualTo("SOFT");
    }

    @Test
    void shouldUpdateExistingEntityFields_whenUpsertingPresentKey() {
        // Arrange — existing row for the key already present.
        SkillDefinition existing = new SkillDefinition("JAVA", "Old name", "TECHNICAL");
        when(repository.findById("JAVA")).thenReturn(Optional.of(existing));
        when(repository.save(any(SkillDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        SkillUpsertRequest req = new SkillUpsertRequest("Java (updated)", "SOFT");

        // Act
        SkillDefinition result = service.upsert("JAVA", req);

        // Assert — fields updated in place, single save call (no duplicate row).
        assertThat(result.getKey()).isEqualTo("JAVA");
        assertThat(result.getDisplayName()).isEqualTo("Java (updated)");
        assertThat(result.getCategory()).isEqualTo("SOFT");
        verify(repository).save(any(SkillDefinition.class));
    }

    @Test
    void shouldInsertAndReturnTrue_whenSeedingAbsentKey() {
        // Arrange
        SkillDefinition def = new SkillDefinition("JAVA", "Java", "TECHNICAL");
        when(repository.existsById("JAVA")).thenReturn(false);

        // Act
        boolean inserted = service.seedIfAbsent(def);

        // Assert
        assertThat(inserted).isTrue();
        verify(repository).save(def);
    }

    @Test
    void shouldSkipSaveAndReturnFalse_whenSeedingPresentKey() {
        // Arrange
        SkillDefinition def = new SkillDefinition("JAVA", "Java", "TECHNICAL");
        when(repository.existsById("JAVA")).thenReturn(true);

        // Act
        boolean inserted = service.seedIfAbsent(def);

        // Assert
        assertThat(inserted).isFalse();
        verify(repository, never()).save(any(SkillDefinition.class));
    }
}
