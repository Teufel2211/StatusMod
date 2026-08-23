import { verifyJwt, type JwtPayload } from "./jwt"
import { verifyArgon2id } from "./hash"

function getSession(request: Request): JwtPayload | null {
  const authHeader = request.headers.get("authorization")
  if (!authHeader?.startsWith("Bearer ")) return null

  const token = authHeader.slice(7)
  return verifyJwt(token)
}

export function requireSession(request: Request): JwtPayload | null {
  const session = getSession(request)
  if (!session) return null
  return session
}

type DataAccess =
  | { kind: "session"; session: JwtPayload }
  | { kind: "apiKey" }
  | null

export async function authorizeData(request: Request, scope: string, serverId: string): Promise<DataAccess> {
  const session = requireSession(request)
  if (session) {
    return session.server_id === serverId ? { kind: "session", session } : null
  }
  if (await requireApiKey(request, scope, serverId)) return { kind: "apiKey" }
  return null
}

export async function resolveApiKeyServer(request: Request, scope: string): Promise<string | null> {
  const apiKey = request.headers.get("x-api-key")
  if (!apiKey) return null

  // Extract prefix (first 10 chars) to pre-filter — avoids Argon2id on every key
  const prefix = apiKey.slice(0, 10)

  const { getServiceClient } = await import("./supabase")
  const sb = getServiceClient()

  const { data, error } = await (sb as any)
    .from("api_keys")
    .select("key_hash, scopes, server_id, revoked, expires_at")
    .is("revoked", false)
    .eq("key_prefix", prefix)

  if (error || !data || data.length === 0) return null

  for (const key of data) {
    const valid = await verifyArgon2id(key.key_hash, apiKey)
    if (valid) {
      if (key.revoked) return null
      if (key.expires_at && new Date(key.expires_at) < new Date()) return null
      if (!key.scopes.includes("*") && !key.scopes.includes(scope)) return null
      return key.server_id as string
    }
  }

  return null
}

export async function requireApiKey(request: Request, scope: string, serverId?: string): Promise<boolean> {
  const resolvedServerId = await resolveApiKeyServer(request, scope)
  if (!resolvedServerId) return false
  if (serverId && resolvedServerId !== serverId) return false
  return true
}
