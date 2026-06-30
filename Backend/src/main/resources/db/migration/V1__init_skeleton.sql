-- V1 — skeleton migration.
-- Only the `job` table (central abstraction, TECHNICAL_DESIGN §2), to confirm the
-- Flyway → Postgres → JPA path works. Full schema: BACKEND_DESIGN §8.

CREATE TABLE job (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type       VARCHAR(32)              NOT NULL,
    status     VARCHAR(32)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Index for the future poller (claim QUEUED by next_run_at). For now just status.
CREATE INDEX idx_job_status ON job (status);
