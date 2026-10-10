-- Fleet config history for rollbacks (new values stored per change).
CREATE TABLE IF NOT EXISTS fleet_config_history (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  dashboard_url text NOT NULL DEFAULT '',
  setup_secret text NOT NULL DEFAULT '',
  by_uuid uuid NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE public.fleet_config_history ENABLE ROW LEVEL SECURITY;

-- NOTE: applied via Supabase migration tool (tracked). Live schema_version
-- uses different columns, so no manual INSERT here.
