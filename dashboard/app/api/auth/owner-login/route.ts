import crypto from "crypto"
import { OwnerCodeSchema } from "@/lib/validators"
import { badRequest, unauthorized, serverError } from "@/lib/response"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders, isLockedOut, lockoutRemainingMs, recordLoginFailure, clearLoginFailures } from "@/lib/rate-limit"
import { NextResponse } from "next/server"
import { getServiceClient } from "@/lib/supabase"
import { signJwt } from "@/lib/jwt"
import { hashIp, hashUserAgent, verifyArgon2id } from "@/lib/hash"
import { refreshCookieHeader } from "@/lib/cookies"

// Redeems an owner one-time login code (issued via /status owner-code).
// Mirrors the player code flow: brute-force lockout, single use, owner
// session with HttpOnly refresh cookie.

export const dynamic = "force-dynamic"

export async function POST(request: Request) {
  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const ipHash = hashIp(ip)

  const lockoutKey = `owner-login-lockout:${ipHash}`
  if (isLockedOut(lockoutKey)) {
    return NextResponse.json(
      { error: "Ungültige Anfrage" },
      { status: 429, headers: { "Retry-After": String(Math.ceil(lockoutRemainingMs(lockoutKey) / 1000)) } }
    )
  }

  const rl = checkRateLimit(`owner-login:${ipHash}`, RATE_LIMITS.LOGIN)
  if (!rl.allowed) {
    return NextResponse.json({ error: "Ungültige Anfrage" }, { status: 429, headers: getRateLimitHeaders(rl) })
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return badRequest()
  }

  const parsed = OwnerCodeSchema.safeParse(body)
  if (!parsed.success) {
    return badRequest()
  }

  const { code } = parsed.data

  const sb = getServiceClient()

  const { data: ownerCodes, error: findError } = await (sb as any)
    .from("owner_login_codes")
    .select("*")
    .is("used", false)
    .gt("expires_at", new Date().toISOString())
    .lt("attempt_count", 3)

  if (findError || !ownerCodes || ownerCodes.length === 0) {
    recordLoginFailure(lockoutKey)
    return unauthorized()
  }

  let matchedCode = null
  for (const oc of ownerCodes) {
    const valid = await verifyArgon2id(oc.code_hash, code)
    if (valid) {
      matchedCode = oc
      break
    }
    await (sb as any)
      .from("owner_login_codes")
      .update({ attempt_count: (oc.attempt_count ?? 0) + 1 })
      .eq("id", oc.id)
  }

  if (!matchedCode) {
    recordLoginFailure(lockoutKey)
    return unauthorized()
  }

  const { error: useError } = await (sb as any)
    .from("owner_login_codes")
    .update({ used: true })
    .eq("id", matchedCode.id)

  if (useError) {
    return unauthorized()
  }

  clearLoginFailures(lockoutKey)

  const { data: server, error: serverError1 } = await (sb as any)
    .from("servers")
    .select("id, owner_uuid")
    .eq("id", matchedCode.server_id)
    .maybeSingle()

  if (serverError1 || !server?.owner_uuid) {
    return unauthorized()
  }

  const { data: user, error: userError } = await (sb as any)
    .from("dashboard_users")
    .select("uuid, role, server_id")
    .eq("uuid", server.owner_uuid)
    .maybeSingle()

  if (userError || !user) {
    return unauthorized()
  }

  const accessToken = signJwt({
    sub: user.uuid,
    server_id: user.server_id,
    role: user.role,
  })

  const refreshTokenRaw = crypto.randomUUID()
  const refreshTokenHash = crypto.createHash("sha256").update(refreshTokenRaw).digest("hex")

  const { error: rtError } = await (sb as any).from("refresh_tokens").insert({
    uuid: user.uuid,
    server_id: user.server_id,
    token_hash: refreshTokenHash,
    expires_at: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
    ip_hash: ipHash,
    user_agent_hash: hashUserAgent(request.headers.get("user-agent") ?? "unknown"),
  })

  if (rtError) {
    return serverError()
  }

  return NextResponse.json(
    { access_token: accessToken, refresh_token: refreshTokenRaw, expires_in: 900, uuid: user.uuid, server_id: user.server_id },
    {
      headers: {
        ...getRateLimitHeaders(rl),
        "Set-Cookie": refreshCookieHeader(refreshTokenRaw),
      },
    }
  )
}
