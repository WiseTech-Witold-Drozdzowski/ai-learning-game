-- V7 — tasks (issue-6, BACKEND_DESIGN §2.3 / §8).
-- Task leaf under a goal; verification routed by task_type_definition.verification_method.
-- HONOR/HONOR_WITH_PROOF submit synchronously (no Job); verification_job_id stays null in PRD-1.

CREATE TABLE task (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    goal_id              BIGINT NOT NULL REFERENCES goal (id) ON DELETE CASCADE,
    type_key             VARCHAR(64) NOT NULL REFERENCES task_type_definition (key),
    title                VARCHAR(255) NOT NULL,
    description          TEXT,
    state                VARCHAR(32) NOT NULL,
    skill_keys           TEXT[] NOT NULL DEFAULT '{}',
    artifact             TEXT,
    exp_awarded          BIGINT NOT NULL DEFAULT 0,
    scheduled_for        TIMESTAMP WITH TIME ZONE,
    verification_job_id  BIGINT,
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_task_goal_id ON task (goal_id);
CREATE INDEX idx_task_state ON task (state);
