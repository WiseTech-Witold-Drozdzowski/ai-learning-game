package com.careercoach.coach.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.careercoach.coach.domain.CoachNote;

/** Persistence for {@link CoachNote}. */
public interface CoachNoteRepository extends JpaRepository<CoachNote, Long> {

    /** Active notes, oldest first — the subset the context assembler injects into the prompt. */
    List<CoachNote> findByActiveTrueOrderByCreatedAtAsc();
}
