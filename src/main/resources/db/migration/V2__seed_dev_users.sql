-- Dev seed data (safe to re-run)
INSERT INTO users (username, display_name, role)
VALUES ('broadcaster1', 'Demo Broadcaster', 'BROADCASTER')
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, display_name, role)
VALUES ('viewer1', 'Demo Viewer', 'VIEWER')
ON CONFLICT (username) DO NOTHING;

INSERT INTO users (username, display_name, role)
VALUES ('dummy_streamer', 'Dummy Streamer', 'BROADCASTER')
ON CONFLICT (username) DO NOTHING;
