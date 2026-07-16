package com.careercoach.coach.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.careercoach.coach.domain.CoachNote;
import com.careercoach.coach.service.CoachNoteService;
import com.careercoach.coach.web.model.CoachNoteUpdateRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * {@code GET/PUT/DELETE /api/coach-notes[/{id}]} (BACKEND_DESIGN §7): the transparency
 * surface over coach memory. The user reads, edits and deletes notes here; notes are
 * <em>created</em> only autonomously by the coach (through the {@code CoachNoteOp} tool),
 * so there is deliberately no user-facing create endpoint. The {@code /api} prefix is
 * applied centrally by {@code ApiPrefixWebConfig}.
 */
@RestController
@RequestMapping("/coach-notes")
@RequiredArgsConstructor
public class CoachNoteController {

    private final CoachNoteService coachNoteService;

    @GetMapping
    public List<CoachNote> list() {
        return coachNoteService.list();
    }

    @GetMapping("/{id}")
    public CoachNote get(@PathVariable Long id) {
        return coachNoteService.get(id);
    }

    @PutMapping("/{id}")
    public CoachNote update(@PathVariable Long id, @Valid @RequestBody CoachNoteUpdateRequest req) {
        return coachNoteService.update(id, req.content(), req.active());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        coachNoteService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
