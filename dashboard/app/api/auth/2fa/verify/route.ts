import { badRequest, serverError, unauthorized } from "@/lib/response"
import { NextResponse } from "next/server"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { decryptSecret, verifyArgon2id, hashIp } from "@/lib/hash"
import { verify as totpVerify } from "otplib"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders } from "@/lib/rate-limit"

export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const rl = checkRateLimit(`totp:${hashIp(ip)}`, RATE_LIMITS.TOTP_VERIFY)
  if (!rl.allowed) {
    return NextResponse.json({ error: "Ungültige Anfrage" }, { status: 429, headers: getRateLimitHeaders(rl) })
  }

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const { token, recovery_code } = body as Record<string, unknown>

  const sb = getServiceClient()
  const { data: user } = await (sb as any)
    .from("dashboard_users")
    .select("totp_secret, recovery_code_hashes")
    .eq("uuid", session.sub)
    .maybeSingle()

  if (!user) return badRequest()

  if (typeof recovery_code === "string" && recovery_code.length >= 6) {
    const hashes: string[] = user.recovery_code_hashes ?? []
    let matched = false
    for (let i = 0; i < hashes.length; i++) {
      const valid = await verifyArgon2id(hashes[i], recovery_code)
      if (valid) {
        hashes.splice(i, 1)
        matched = true
        break
      }
    }
    if (!matched) return badRequest()

    const { error } = await (sb as any)
      .from("dashboard_users")
      .update({ totp_enabled: true, recovery_code_hashes: hashes })
      .eq("uuid", session.sub)

    if (error) return serverError()
    return NextResponse.json({ success: true, used_recovery: true })
  }

  if (typeof token !== "string" || token.length < 6) return badRequest()

  if (!user.totp_secret) return badRequest()

  const decrypted = decryptSecret(user.totp_secret)
  if (!decrypted) return badRequest()

  const isValid = totpVerify({ token, secret: decrypted })
  if (!isValid) return badRequest()

  const { error } = await (sb as any)
    .from("dashboard_users")
    .update({ totp_enabled: true })
    .eq("uuid", session.sub)

  if (error) return serverError()

  return NextResponse.json({ success: true })
}
