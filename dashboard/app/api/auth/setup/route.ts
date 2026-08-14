import { SetupCodeSchema } from "@/lib/validators"
import { badRequest, unauthorized, serverError } from "@/lib/response"
import { NextResponse } from "next/server"
import { getServiceClient } from "@/lib/supabase"
import { signJwt } from "@/lib/jwt"
import { hashIp, hashArgon2id, verifyArgon2id, encryptSecret } from "@/lib/hash"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders } from "@/lib/rate-limit"
import { refreshCookieHeader } from "@/lib/cookies"
import crypto from "crypto"

export async function POST(request: Request) {
  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const rl = checkRateLimit(`setup:${hashIp(ip)}`, RATE_LIMITS.LOGIN)
  if (!rl.allowed) {
    return NextResponse.json({ error: "Ungültige Anfrage" }, { status: 429, headers: getRateLimitHeaders(rl) })
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return badRequest()
  }

  const parsed = SetupCodeSchema.safeParse(body)
  if (!parsed.success) {
    return badRequest()
  }

  const { code } = parsed.data
  const sb = getServiceClient()

  const { data: codes, error: codeError } = await (sb as any)
    .from("dashboard_codes")
    .select("*")
    .is("used", false)
    .gt("expires_at", new Date().toISOString())

  if (codeError || !codes || codes.length === 0) {
    return unauthorized()
  }

  let matchedCode = null
  for (const c of codes) {
    const valid = await verifyArgon2id(c.code_hash, code)
    if (valid) {
      matchedCode = c
      break
    }
  }

  if (!matchedCode) {
    return unauthorized()
  }

  const { error: useError } = await (sb as any)
    .from("dashboard_codes")
    .delete()
    .eq("server_id", matchedCode.server_id)
    .eq("code_hash", matchedCode.code_hash)

  if (useError) {
    return serverError()
  }

  const uuid = crypto.randomUUID()

  const { error: userError } = await (sb as any).from("dashboard_users").insert({
    uuid,
    username: "owner",
    role: "owner",
    server_id: matchedCode.server_id,
  })

  if (userError) {
    return serverError()
  }

  const { error: ownerError } = await (sb as any)
    .from("servers")
    .update({ owner_uuid: uuid })
    .eq("id", matchedCode.server_id)

  if (ownerError) {
    return serverError()
  }

  const rawApiKey = `sm_${crypto.randomBytes(32).toString("hex")}`
  const apiKeyHash = await hashArgon2id(rawApiKey)
  const apiKeyPrefix = rawApiKey.slice(0, 10)

  const { error: keyError } = await (sb as any).from("api_keys").insert({
    server_id: matchedCode.server_id,
    key_hash: apiKeyHash,
    key_prefix: apiKeyPrefix,
    scopes: ["check"],
    expires_at: new Date(Date.now() + 365 * 24 * 60 * 60 * 1000).toISOString(),
  })

  if (keyError) {
    return serverError()
  }

  const { error: pendingKeyError } = await (sb as any).from("setup_pending_keys").insert({
    server_id: matchedCode.server_id,
    key_encrypted: encryptSecret(rawApiKey),
  })

  if (pendingKeyError) {
    return serverError()
  }

  const accessToken = signJwt({ sub: uuid, server_id: matchedCode.server_id, role: "owner" })

  const refreshTokenRaw = crypto.randomUUID()
  const refreshTokenHash = crypto.createHash("sha256").update(refreshTokenRaw).digest("hex")
  const { error: rtError } = await (sb as any).from("refresh_tokens").insert({
    uuid,
    server_id: matchedCode.server_id,
    token_hash: refreshTokenHash,
    expires_at: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    ip_hash: hashIp(ip),
    user_agent_hash: crypto.createHash("sha256").update(request.headers.get("user-agent") ?? "unknown").digest("hex"),
  })

  if (rtError) {
    return serverError()
  }

  return NextResponse.json(
    {
      access_token: accessToken,
      refresh_token: refreshTokenRaw,
      expires_in: 900,
      server_id: matchedCode.server_id,
      api_key: rawApiKey,
      key_prefix: apiKeyPrefix,
    },
    {
      headers: {
        ...getRateLimitHeaders(rl),
        "Set-Cookie": refreshCookieHeader(refreshTokenRaw),
      },
    }
  )
}
