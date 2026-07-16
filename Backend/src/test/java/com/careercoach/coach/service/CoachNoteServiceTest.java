package com.careercoach.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.careercoach.coach.domain.CoachNote;
import com.careercoach.coach.domain.exception.CoachNoteNotFoundException;
import com.careercoach.coach.repository.CoachNoteRepository;

/**
 * Unit test for {@link CoachNoteService} (issue-7, BACKEND_DESIGN §2.6): the coach's
 * autonomous {@link #applyOps} tool (CREATE / UPDATE), plus the user-facing CRUD and
 * the {@code listActive} subset consumed by the assembler. Red phase: the skeleton
 * throws {@code UnsupportedOperationException}.
 */
@ExtendWith(MockitoExtension.class)
class CoachNoteServiceTest {

    @Mock
    private CoachNoteRepository coachNoteRepository;

    @InjectMocks
    private CoachNoteService coachNoteService;

    private static CoachNote note(Long id, String content, boolean active) {
        return CoachNote.builder().id(id).content(content).active(active).build();
    }

    @Test
    void applyOps_shouldCreateNewActiveNote_whenActionCreate() {
        // Arrange
        List<CoachNoteOp> ops = List.of(
                new CoachNoteOp(CoachNoteOp.Action.CREATE, null, "Prefers hands-on over theory"));
        when(coachNoteRepository.save(any(CoachNote.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        coachNoteService.applyOps(ops);

        // Assert — a new, active note is persisted with the coach's content
        ArgumentCaptor<CoachNote> captor = ArgumentCaptor.forClass(CoachNote.class);
        verify(coachNoteRepository).save(captor.capture());
        CoachNote saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getContent()).isEqualTo("Prefers hands-on over theory");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void applyOps_shouldRewriteExistingNote_whenActionUpdate() {
        // Arrange — an existing note the coach chooses to refine
        CoachNote existing = note(9L, "old observation", true);
        when(coachNoteRepository.findById(9L)).thenReturn(Optional.of(existing));
        when(coachNoteRepository.save(any(CoachNote.class))).thenAnswer(inv -> inv.getArgument(0));
        List<CoachNoteOp> ops = List.of(
                new CoachNoteOp(CoachNoteOp.Action.UPDATE, 9L, "refined observation"));

        // Act
        coachNoteService.applyOps(ops);

        // Assert — content rewritten in place, saved back
        ArgumentCaptor<CoachNote> captor = ArgumentCaptor.forClass(CoachNote.class);
        verify(coachNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(9L);
        assertThat(captor.getValue().getContent()).isEqualTo("refined observation");
    }

    @Test
    void applyOps_shouldThrow_whenUpdatingMissingNote() {
        // Arrange
        when(coachNoteRepository.findById(404L)).thenReturn(Optional.empty());
        List<CoachNoteOp> ops = List.of(
                new CoachNoteOp(CoachNoteOp.Action.UPDATE, 404L, "content"));

        // Act / Assert
        assertThatThrownBy(() -> coachNoteService.applyOps(ops))
                .isInstanceOf(CoachNoteNotFoundException.class);
    }

    @Test
    void applyOps_shouldBeNoOp_whenBatchNullOrEmpty() {
        // Act — null and empty are both tolerated (LLM chose to make no memory changes)
        coachNoteService.applyOps(null);
        coachNoteService.applyOps(List.of());

        // Assert — nothing touched
        verifyNoInteractions(coachNoteRepository);
    }

    @Test
    void list_shouldReturnAllNotes_activeAndInactive() {
        // Arrange
        when(coachNoteRepository.findAll())
                .thenReturn(List.of(note(1L, "a", true), note(2L, "b", false)));

        // Act
        List<CoachNote> result = coachNoteService.list();

        // Assert — the user sees everything, regardless of active flag
        assertThat(result).extracting(CoachNote::getContent).containsExactly("a", "b");
    }

    @Test
    void listActive_shouldReturnOnlyActiveNotes() {
        // Arrange — the assembler subset comes from the active-only query
        when(coachNoteRepository.findByActiveTrueOrderByCreatedAtAsc())
                .thenReturn(List.of(note(1L, "active one", true)));

        // Act
        List<CoachNote> result = coachNoteService.listActive();

        // Assert
        assertThat(result).extracting(CoachNote::getContent).containsExactly("active one");
        verify(coachNoteRepository).findByActiveTrueOrderByCreatedAtAsc();
    }

    @Test
    void get_shouldReturnNote_whenExists() {
        // Arrange
        when(coachNoteRepository.findById(3L)).thenReturn(Optional.of(note(3L, "found", true)));

        // Act
        CoachNote result = coachNoteService.get(3L);

        // Assert
        assertThat(result.getContent()).isEqualTo("found");
    }

    @Test
    void get_shouldThrow_whenMissing() {
        // Arrange
        when(coachNoteRepository.findById(3L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> coachNoteService.get(3L))
                .isInstanceOf(CoachNoteNotFoundException.class);
    }

    @Test
    void update_shouldRewriteContentAndActive_whenExists() {
        // Arrange
        CoachNote existing = note(5L, "before", true);
        when(coachNoteRepository.findById(5L)).thenReturn(Optional.of(existing));
        when(coachNoteRepository.save(any(CoachNote.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act — user deactivates and rewrites the note
        CoachNote result = coachNoteService.update(5L, "after", false);

        // Assert
        assertThat(result.getContent()).isEqualTo("after");
        assertThat(result.isActive()).isFalse();
        verify(coachNoteRepository).save(existing);
    }

    @Test
    void update_shouldThrow_whenMissing() {
        // Arrange
        when(coachNoteRepository.findById(5L)).thenReturn(Optional.empty());

        // Act / Assert
        assertThatThrownBy(() -> coachNoteService.update(5L, "x", true))
                .isInstanceOf(CoachNoteNotFoundException.class);
        verify(coachNoteRepository, never()).save(any());
    }

    @Test
    void delete_shouldRemoveNote_whenExists() {
        // Arrange
        when(coachNoteRepository.existsById(7L)).thenReturn(true);

        // Act
        coachNoteService.delete(7L);

        // Assert
        verify(coachNoteRepository).deleteById(7L);
    }

    @Test
    void delete_shouldThrow_whenMissing() {
        // Arrange
        when(coachNoteRepository.existsById(7L)).thenReturn(false);

        // Act / Assert
        assertThatThrownBy(() -> coachNoteService.delete(7L))
                .isInstanceOf(CoachNoteNotFoundException.class);
        verify(coachNoteRepository, never()).deleteById(any());
    }
}
