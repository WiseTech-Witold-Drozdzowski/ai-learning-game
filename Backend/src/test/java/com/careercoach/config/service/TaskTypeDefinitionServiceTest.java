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

import com.careercoach.config.domain.TaskTypeDefinition;
import com.careercoach.config.domain.VerificationMethod;
import com.careercoach.config.repository.TaskTypeDefinitionRepository;
import com.careercoach.config.web.TaskTypeUpsertRequest;

/**
 * Unit tests for {@link TaskTypeDefinitionService} (issue-2) — repository is
 * mocked. Red phase: the skeleton throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class TaskTypeDefinitionServiceTest {

    @Mock
    private TaskTypeDefinitionRepository repository;

    private TaskTypeDefinitionService service;

    @BeforeEach
    void setUp() {
        service = new TaskTypeDefinitionService(repository);
    }

    @Test
    void shouldReturnAllEntriesOrderedByKey_whenListed() {
        // Arrange — repository returns entries out of order; the service must sort by key.
        TaskTypeDefinition zKey = new TaskTypeDefinition("Z_KEY", "Z", VerificationMethod.HONOR, 10, false, false);
        TaskTypeDefinition aKey = new TaskTypeDefinition("A_KEY", "A", VerificationMethod.HONOR, 10, false, false);
        lenient().when(repository.findAll()).thenReturn(List.of(zKey, aKey));
        lenient().when(repository.findAll(any(Sort.class))).thenReturn(List.of(aKey, zKey));

        // Act
        List<TaskTypeDefinition> result = service.list();

        // Assert
        assertThat(result).extracting(TaskTypeDefinition::getKey).containsExactly("A_KEY", "Z_KEY");
    }

    @Test
    void shouldReturnEntry_whenPresent() {
        // Arrange
        TaskTypeDefinition def = new TaskTypeDefinition("HONOR_CHECK", "Honor check",
                VerificationMethod.HONOR, 10, false, false);
        when(repository.findById("HONOR_CHECK")).thenReturn(Optional.of(def));

        // Act
        TaskTypeDefinition result = service.get("HONOR_CHECK");

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
        when(repository.findById("NEW_KEY")).thenReturn(Optional.empty());
        ArgumentCaptor<TaskTypeDefinition> captor = ArgumentCaptor.forClass(TaskTypeDefinition.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> captor.getValue());
        TaskTypeUpsertRequest req = new TaskTypeUpsertRequest("New Type", VerificationMethod.HONOR_WITH_PROOF, 15, true, true);

        // Act
        TaskTypeDefinition result = service.upsert("NEW_KEY", req);

        // Assert
        assertThat(result.getKey()).isEqualTo("NEW_KEY");
        assertThat(result.getDisplayName()).isEqualTo("New Type");
        assertThat(result.getVerificationMethod()).isEqualTo(VerificationMethod.HONOR_WITH_PROOF);
        assertThat(result.getExpBase()).isEqualTo(15);
        assertThat(result.isExpScaleByScore()).isTrue();
        assertThat(result.isRequiresArtifact()).isTrue();
    }

    @Test
    void shouldUpdateExistingEntityFields_whenUpsertingPresentKey() {
        // Arrange — existing row for the key already present.
        TaskTypeDefinition existing = new TaskTypeDefinition("HONOR_CHECK", "Old name",
                VerificationMethod.HONOR, 5, false, false);
        when(repository.findById("HONOR_CHECK")).thenReturn(Optional.of(existing));
        when(repository.save(any(TaskTypeDefinition.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TaskTypeUpsertRequest req = new TaskTypeUpsertRequest("Updated name", VerificationMethod.HONOR_WITH_PROOF, 25, true, true);

        // Act
        TaskTypeDefinition result = service.upsert("HONOR_CHECK", req);

        // Assert — fields updated in place, single save call (no duplicate row).
        assertThat(result.getKey()).isEqualTo("HONOR_CHECK");
        assertThat(result.getDisplayName()).isEqualTo("Updated name");
        assertThat(result.getVerificationMethod()).isEqualTo(VerificationMethod.HONOR_WITH_PROOF);
        assertThat(result.getExpBase()).isEqualTo(25);
        assertThat(result.isExpScaleByScore()).isTrue();
        assertThat(result.isRequiresArtifact()).isTrue();
        verify(repository).save(any(TaskTypeDefinition.class));
    }

    @Test
    void shouldInsertAndReturnTrue_whenSeedingAbsentKey() {
        // Arrange
        TaskTypeDefinition def = new TaskTypeDefinition("HONOR_CHECK", "Honor check",
                VerificationMethod.HONOR, 10, false, false);
        when(repository.existsById("HONOR_CHECK")).thenReturn(false);

        // Act
        boolean inserted = service.seedIfAbsent(def);

        // Assert
        assertThat(inserted).isTrue();
        verify(repository).save(def);
    }

    @Test
    void shouldSkipSaveAndReturnFalse_whenSeedingPresentKey() {
        // Arrange
        TaskTypeDefinition def = new TaskTypeDefinition("HONOR_CHECK", "Honor check",
                VerificationMethod.HONOR, 10, false, false);
        when(repository.existsById("HONOR_CHECK")).thenReturn(true);

        // Act
        boolean inserted = service.seedIfAbsent(def);

        // Assert
        assertThat(inserted).isFalse();
        verify(repository, never()).save(any(TaskTypeDefinition.class));
    }
}
