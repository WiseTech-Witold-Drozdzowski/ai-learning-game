package com.careercoach.coach.web.model;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code PUT /api/coach-notes/{id}} — the user's edit of a coach note:
 * new {@code content} and whether the note stays {@code active} (active notes enter
 * the assembler; deactivating one keeps it visible but out of the prompt).
 */
public record CoachNoteUpdateRequest(@NotBlank String content, boolean active) {
}
