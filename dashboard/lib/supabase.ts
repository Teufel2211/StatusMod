import { createClient } from "@supabase/supabase-js"
import { getEnv } from "./env"

let _serviceClient: ReturnType<typeof createClient> | null = null

export function getServiceClient() {
  if (_serviceClient) return _serviceClient
  const env = getEnv()
  _serviceClient = createClient(env.NEXT_PUBLIC_SUPABASE_URL, env.SERVICE_ROLE_KEY, {
    auth: { persistSession: false, autoRefreshToken: false },
  })
  return _serviceClient
}
