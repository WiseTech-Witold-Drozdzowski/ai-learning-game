-- V11 — coach memory (issue-7, BACKEND_DESIGN §2.6 / §8).
-- Lightweight narrative memory that makes the coach "remember me": durable observations
-- about the user (e.g. "prefers hands-on over theory"). The coach manages notes
-- AUTONOMOUSLY (through a structured tool during PLANNING/EVALUATION), but they are JAWNE
-- and editable by the user (transparency) via GET/PUT/DELETE /api/coach-notes.
-- Active notes are assembled into the planning/evaluation prompt (the seam left in issue-2);
-- inactive notes are kept but excluded from the prompt. Mock sessions contribute only a
-- distillate here, never the raw transcript.

CREATE TABLE coach_note (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    content     TEXT NOT NULL,
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_coach_note_active ON coach_note (active);
