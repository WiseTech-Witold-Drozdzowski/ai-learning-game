-- V9 — AUTO_QUIZ (issue-5, BACKEND_DESIGN §2.3 / §3).
-- The AI-generated quiz (questions + answer key) is persisted as part of the task's
-- state so grading at submit time is deterministic. JSONB: not queried relationally.

ALTER TABLE task ADD COLUMN quiz JSONB;
