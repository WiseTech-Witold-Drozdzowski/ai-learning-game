-- V4 — goal tree (issue-3, BACKEND_DESIGN §2.2 / §8).
-- Recursive Goal (adjacency list via parent_id). kind: STRATEGIC (root) | LEVEL (nested).
-- state machine PROPOSED -> ACCEPTED -> ACTIVE -> CLOSED. exp_earned stays 0 (bubbling in issue-5).

CREATE TABLE goal (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    parent_id    BIGINT REFERENCES goal (id) ON DELETE CASCADE,
    kind         VARCHAR(32)  NOT NULL,
    title        VARCHAR(255) NOT NULL,
    description  TEXT,
    state        VARCHAR(32)  NOT NULL,
    created_by   VARCHAR(32)  NOT NULL,
    order_index  INTEGER      NOT NULL DEFAULT 0,
    exp_earned   BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_goal_parent_id ON goal (parent_id);
