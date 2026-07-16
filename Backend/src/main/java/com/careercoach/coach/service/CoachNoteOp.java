package com.careercoach.coach.service;

/**
 * A single autonomous coach-memory operation (issue-7). The coach manages
 * {@code coach_notes} through this <b>structured tool</b> — not by emitting free text —
 * so the note lifecycle stays explicit and auditable. Parsed out of the LLM reply
 * ({@code PlanningLlmResponse}/{@code EvaluationLlmResponse}) and applied by
 * {@link CoachNoteService#applyOps}.
 *
 * <p>{@code CREATE} adds a new note from {@code content} ({@code id} ignored);
 * {@code UPDATE} rewrites the note identified by {@code id} to {@code content}.
 */
public record CoachNoteOp(Action action, Long id, String content) {

    public enum Action {
        CREATE,
        UPDATE
    }
}
