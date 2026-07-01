-- V5 — make the self-referential goal.parent_id FK deferrable (issue-3 fix).
--
-- The goals slice is wiped between tests with a single goalRepository.deleteAll(),
-- which deletes rows in a single transaction in id (parent-before-child) order.
-- A self-referential FK validated per-statement aborts that wipe with
-- "violates foreign key constraint goal_parent_id_fkey ... still referenced".
--
-- DEFERRABLE INITIALLY DEFERRED defers the constraint check to COMMIT, when the
-- table is already consistent (empty), so any delete order within a transaction
-- succeeds. A new migration version (rather than editing V4) is required so it
-- also reaches databases that already have V4 applied.
ALTER TABLE goal DROP CONSTRAINT goal_parent_id_fkey;

ALTER TABLE goal
    ADD CONSTRAINT goal_parent_id_fkey
    FOREIGN KEY (parent_id) REFERENCES goal (id) DEFERRABLE INITIALLY DEFERRED;
