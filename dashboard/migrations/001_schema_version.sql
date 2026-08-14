-- Schema version tracking (run once)
CREATE TABLE IF NOT EXISTS schema_version (
  version INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  checksum TEXT NOT NULL
);

-- Current version
INSERT INTO schema_version (version, name, checksum)
VALUES (1, 'initial-schema', 'sha256-initial')
ON CONFLICT (version) DO NOTHING;
