-- Owner one-time login codes: issued via server console (/status owner-code),
-- redeemed on the dashboard login page for an owner session (cookie).
CREATE TABLE IF NOT EXISTS owner_login_codes (
  id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  server_id uuid NOT NULL,
  code_hash text NOT NULL,
  expires_at timestamptz NOT NULL,
  used boolean NOT NULL DEFAULT false,
  attempt_count int NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS owner_login_codes_server_idx ON owner_login_codes (server_id);

-- NOTE: applied via Supabase migration tool. The live schema_version table
-- uses (id, version text, applied_at, checksum), so no manual INSERT here.
