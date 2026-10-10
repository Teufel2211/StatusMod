-- Fleet config: global values distributed to all mod servers via pull
-- (dashboard_url + setup_secret for secret rotation without file access).
CREATE TABLE IF NOT EXISTS fleet_config (
  id int PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  dashboard_url text NOT NULL DEFAULT '',
  setup_secret text NOT NULL DEFAULT '',
  updated_at timestamptz NOT NULL DEFAULT now()
);
INSERT INTO fleet_config (id) VALUES (1) ON CONFLICT (id) DO NOTHING;

-- NOTE: applied via Supabase migration tool (tracked as 'fleet-config-table').
-- The live schema_version table uses (id, version text, applied_at, checksum),
-- so no manual INSERT here (kept in sync with 001/002 conventions otherwise).
