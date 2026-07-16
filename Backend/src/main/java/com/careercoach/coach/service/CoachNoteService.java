package com.careercoach.coach.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.careercoach.coach.domain.CoachNote;
import com.careercoach.coach.domain.exception.CoachNoteNotFoundException;
import com.careercoach.coach.repository.CoachNoteRepository;

import lombok.RequiredArgsConstructor;

/**
 * Coach memory (issue-7, BACKEND_DESIGN §2.6). Two audiences share one store:
 * the user reads/edits/deletes notes for transparency ({@link #list}, {@link #get},
 * {@link #update}, {@link #delete}), while the coach mutates them autonomously through
 * the structured {@link CoachNoteOp} tool ({@link #applyOps}). {@link #listActive} is the
 * subset the {@link ContextAssembler} injects into the planning/evaluation prompt.
 */
@Service
@Transactional
@RequiredArgsConstructor
public class CoachNoteService {

    private final CoachNoteRepository coachNoteRepository;

    /** All notes (active and inactive) — what the user sees in the UI. */
    @Transactional(readOnly = true)
    public List<CoachNote> list() {
        return coachNoteRepository.findAll();
    }

    /** A single note by id, or {@link CoachNoteNotFoundException} if it does not exist. */
    @Transactional(readOnly = true)
    public CoachNote get(Long id) {
        return coachNoteRepository.findById(id)
                .orElseThrow(() -> new CoachNoteNotFoundException(id));
    }

    /** Active notes only — the subset assembled into the coach prompt. */
    @Transactional(readOnly = true)
    public List<CoachNote> listActive() {
        return coachNoteRepository.findByActiveTrueOrderByCreatedAtAsc();
    }

    /** User edit: rewrite a note's content and active flag. */
    public CoachNote update(Long id, String content, boolean active) {
        CoachNote note = get(id);
        note.setContent(content);
        note.setActive(active);
        return coachNoteRepository.save(note);
    }

    /** User delete: remove a note entirely. */
    public void delete(Long id) {
        if (!coachNoteRepository.existsById(id)) {
            throw new CoachNoteNotFoundException(id);
        }
        coachNoteRepository.deleteById(id);
    }

    /**
     * Apply a batch of autonomous coach operations (the "tool"): {@code CREATE} adds a new
     * active note, {@code UPDATE} rewrites an existing one. A {@code null}/empty batch is a
     * no-op (the coach chose to make no memory changes).
     */
    public void applyOps(List<CoachNoteOp> ops) {
        if (ops == null || ops.isEmpty()) {
            return;
        }
        for (CoachNoteOp op : ops) {
            switch (op.action()) {
                case CREATE -> coachNoteRepository.save(
                        CoachNote.builder().content(op.content()).active(true).build());
                case UPDATE -> {
                    CoachNote note = get(op.id());
                    note.setContent(op.content());
                    coachNoteRepository.save(note);
                }
            }
        }
    }
}
