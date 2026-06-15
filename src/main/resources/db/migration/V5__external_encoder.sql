-- Encode plane: app does not spawn FFmpeg when external_encoder is true (remote FFmpeg RTMP).

ALTER TABLE live_streams ADD COLUMN IF NOT EXISTS external_encoder BOOLEAN NOT NULL DEFAULT FALSE;
