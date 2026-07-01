-- V3 — config catalog (issue-2, BACKEND_DESIGN §2.4).
-- task_type_definition + skill_definition: curated, runtime-editable catalog
-- referenced by Task.typeKey / Task.skillKeys[] and GamificationService.
-- Seeded from YAML defaults at startup (insert-if-absent); DB is source of truth.

CREATE TABLE task_type_definition (
    key                   VARCHAR(64)  PRIMARY KEY,
    display_name          VARCHAR(255) NOT NULL,
    verification_method   VARCHAR(32)  NOT NULL,
    exp_base              INTEGER      NOT NULL,
    exp_scale_by_score    BOOLEAN      NOT NULL,
    requires_artifact     BOOLEAN      NOT NULL
);

CREATE TABLE skill_definition (
    key           VARCHAR(64)  PRIMARY KEY,
    display_name  VARCHAR(255) NOT NULL,
    category      VARCHAR(255) NOT NULL
);
