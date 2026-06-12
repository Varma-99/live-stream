-- Phase 2: Control Plane metadata schema (PostgreSQL)

CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(64)  NOT NULL UNIQUE,
    display_name    VARCHAR(128) NOT NULL,
    role            VARCHAR(32)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS live_streams (
    id              BIGSERIAL PRIMARY KEY,
    broadcaster_id  BIGINT       NOT NULL REFERENCES users (id),
    title           VARCHAR(255) NOT NULL,
    stream_key      VARCHAR(64)  NOT NULL UNIQUE,
    status          VARCHAR(32)  NOT NULL,
    view_count      BIGINT       NOT NULL DEFAULT 0,
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_live_streams_status ON live_streams (status);
CREATE INDEX IF NOT EXISTS idx_live_streams_broadcaster ON live_streams (broadcaster_id);
