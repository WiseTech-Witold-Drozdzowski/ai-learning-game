-- V6 — gamification engine (issue-5, BACKEND_DESIGN §2.5 / §8).
-- skill: per-user progress cache for a skill_definition. exp_event: append-only ledger,
-- source of truth for exp; skill.exp / goal.exp_earned / career_profile.total_exp are
-- denormalized counters derived from it.

CREATE TABLE skill (
    key   VARCHAR(64) PRIMARY KEY REFERENCES skill_definition (key),
    level INTEGER NOT NULL DEFAULT 1,
    exp   BIGINT  NOT NULL DEFAULT 0
);

CREATE TABLE exp_event (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_task_id  BIGINT NOT NULL,
    attempt_id      BIGINT,
    skill_key       VARCHAR(64) REFERENCES skill_definition (key),
    amount          BIGINT NOT NULL,
    reason          VARCHAR(64) NOT NULL,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_exp_event_source_task_id ON exp_event (source_task_id);
