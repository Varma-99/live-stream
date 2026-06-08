-- QoS session persistence (postmortem history across restarts)

ALTER TABLE live_streams ADD COLUMN IF NOT EXISTS delivery VARCHAR(16);

CREATE TABLE IF NOT EXISTS qos_sessions (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    stream_id       BIGINT NOT NULL,
    started_at_ms   BIGINT NOT NULL,
    ended_at_ms     BIGINT,
    zombie_stop     BOOLEAN NOT NULL DEFAULT FALSE,
    overall_score   INT,
    root_cause      VARCHAR(32),
    duration_ms     BIGINT,
    delivery_mode   VARCHAR(16),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qos_sessions_stream FOREIGN KEY (stream_id) REFERENCES live_streams(id)
);

CREATE TABLE IF NOT EXISTS qos_events (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      BIGINT NOT NULL,
    at_epoch_ms     BIGINT NOT NULL,
    stage           VARCHAR(16) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    message         CLOB,
    detail          CLOB,
    CONSTRAINT fk_qos_events_session FOREIGN KEY (session_id) REFERENCES qos_sessions(id)
);

CREATE TABLE IF NOT EXISTS qos_viewer_sessions (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id          BIGINT NOT NULL,
    presence_id         VARCHAR(64),
    quality_label       VARCHAR(16),
    watch_ms            BIGINT,
    ttff_ms             BIGINT,
    stalls              INT,
    quality_switches    INT,
    packet_loss_pct     DOUBLE,
    rtt_ms              BIGINT,
    jitter_ms           DOUBLE,
    download_kbps       BIGINT,
    CONSTRAINT fk_qos_viewer_sessions_session FOREIGN KEY (session_id) REFERENCES qos_sessions(id)
);
