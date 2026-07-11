-- V10 — mock interview (issue-6, BACKEND_DESIGN §2.6 / §8).
-- Interactive session where the coach quizzes the user. The transcript lives in a
-- SEPARATE mock_message table (decision A) written incrementally (message-by-message)
-- so an interruption mid-session keeps everything already said. The transcript is
-- readable for review but never enters the context assembler (long-term coach memory);
-- only the result (score / ExpEvent) survives it.

CREATE TABLE mock_session (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id      BIGINT NOT NULL REFERENCES task (id) ON DELETE CASCADE,
    state        VARCHAR(32) NOT NULL,
    score        INTEGER,
    started_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    finished_at  TIMESTAMP WITH TIME ZONE
);

CREATE TABLE mock_message (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id  BIGINT NOT NULL REFERENCES mock_session (id) ON DELETE CASCADE,
    role        VARCHAR(16) NOT NULL,
    content     TEXT NOT NULL,
    seq         INTEGER NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_mock_session_task_id ON mock_session (task_id);
CREATE INDEX idx_mock_message_session_id ON mock_message (session_id);
CREATE UNIQUE INDEX uq_mock_message_session_seq ON mock_message (session_id, seq);
