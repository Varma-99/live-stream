-- Dummy streamers: test-pattern FFmpeg, independent of main camera stream

ALTER TABLE live_streams ADD COLUMN IF NOT EXISTS dummy_stream BOOLEAN NOT NULL DEFAULT FALSE;

INSERT INTO users (username, display_name, role)
VALUES ('dummy_streamer', 'Dummy Streamer', 'BROADCASTER')
ON CONFLICT (username) DO NOTHING;
