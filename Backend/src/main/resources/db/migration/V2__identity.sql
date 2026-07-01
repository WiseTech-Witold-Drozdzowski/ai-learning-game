-- V2 — identity foundation (issue-1, BACKEND_DESIGN §2.1 / §8).
-- users + career_profile (1:1, shared PK). avatar_state as JSONB, mapped to the
-- typed AvatarState record via native Hibernate 6 (@JdbcTypeCode SqlTypes.JSON).

CREATE TABLE users (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email        VARCHAR(255)             NOT NULL UNIQUE,
    google_sub   VARCHAR(255),
    display_name VARCHAR(255),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE career_profile (
    user_id      BIGINT  PRIMARY KEY REFERENCES users (id),
    total_exp    BIGINT  NOT NULL DEFAULT 0,
    level        INTEGER NOT NULL DEFAULT 1,
    avatar_state JSONB   NOT NULL DEFAULT '{}'::jsonb
);
