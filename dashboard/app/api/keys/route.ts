import { ApiKeyCreateSchema } from "@/lib/validators"
import { badRequest, ok, created, serverError, unauthorized, tooMany } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders } from "@/lib/rate-limit"
import { hashIp, hashArgon2id } from "@/lib/hash"
import crypto from "crypto"

export async function GET(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  const sb = getServiceClient()
  const { data, error } = await (sb as any)
    .from("api_keys")
    .select("id, key_prefix, scopes, created_at, expires_at, revoked")
    .eq("server_id", session.server_id)
    .order("created_at", { ascending: false })

  if (error) return serverError()

  return ok(data)
}

export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const rl = checkRateLimit(`key_create:${hashIp(ip)}`, { windowMs: 60_000, max: 3 })
  if (!rl.allowed) {
    return tooMany()
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return badRequest()
  }

  const parsed = ApiKeyCreateSchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const sb = getServiceClient()

  const { data: existingKeys, error: countError } = await (sb as any)
    .from("api_keys")
    .select("id", { count: "exact" })
    .eq("server_id", session.server_id)
    .is("revoked", false)

  if (countError) return serverError()

  if (existingKeys && existingKeys.length >= 2) {
    return badRequest()
  }

  const rawKey = `sm_${crypto.randomBytes(32).toString("hex")}`
  const keyHash = await hashArgon2id(rawKey)
  const keyPrefix = rawKey.slice(0, 10)

  const { error: insertError } = await (sb as any).from("api_keys").insert({
    server_id: session.server_id,
    key_hash: keyHash,
    key_prefix: keyPrefix,
    scopes: parsed.data.scopes,
    expires_at: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(),
  })

  if (insertError) return serverError()

  return created({
    api_key: rawKey,
    key_prefix: keyPrefix,
    scopes: parsed.data.scopes,
    expires_at: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(),
  })
}
