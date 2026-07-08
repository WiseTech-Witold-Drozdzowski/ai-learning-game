-- V8 — full `job` shape (BACKEND_DESIGN §8): typed JSONB input/output, retry/backoff,
-- locking, and lifecycle timestamps. Extends the V1 skeleton; V1 and idx_job_status stay.

ALTER TABLE job
    ADD COLUMN input           JSONB,
    ADD COLUMN output          JSONB,
    ADD COLUMN related_goal_id BIGINT,
    ADD COLUMN related_task_id BIGINT,
    ADD COLUMN attempts        INTEGER                  NOT NULL DEFAULT 0,
    ADD COLUMN max_attempts    INTEGER                  NOT NULL DEFAULT 3,
    ADD COLUMN next_run_at     TIMESTAMP WITH TIME ZONE,
    ADD COLUMN locked_at       TIMESTAMP WITH TIME ZONE,
    ADD COLUMN error           TEXT,
    ADD COLUMN started_at      TIMESTAMP WITH TIME ZONE,
    ADD COLUMN finished_at     TIMESTAMP WITH TIME ZONE;

-- Poller claim index: QUEUED ordered by due time.
CREATE INDEX idx_job_status_next_run_at ON job (status, next_run_at);
