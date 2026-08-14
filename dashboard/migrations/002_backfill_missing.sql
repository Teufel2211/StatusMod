-- Backfill: add recovery_code_hashes to existing users if missing
ALTER TABLE dashboard_users ADD COLUMN IF NOT EXISTS recovery_code_hashes TEXT[] DEFAULT '{}';

-- Add schema_version entry
INSERT INTO schema_version (version, name, checksum)
VALUES (2, 'recovery-codes-column', 'sha256-recovery')
ON CONFLICT (version) DO NOTHING;
