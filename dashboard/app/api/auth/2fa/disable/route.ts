import { ok, serverError, unauthorized, tooMany, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { checkRateLimit, RATE_LIMITS } from "@/lib/rate-limit"
import { hashIp, decryptSecret } from "@/lib/hash"
import { verify as totpVerify } from "otplib"

export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const rl = checkRateLimit(`totp:${hashIp(ip)}`, RATE_LIMITS.TOTP_VERIFY)
  if (!rl.allowed) {
    return tooMany()
  }

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const { token } = body as Record<string, unknown>
  if (typeof token !== "string" || token.length < 6) {
    return badRequest()
  }

  const sb = getServiceClient()
  const { data: user } = await (sb as any)
    .from("dashboard_users")
    .select("totp_secret, totp_enabled")
    .eq("uuid", session.sub)
    .maybeSingle()

  if (!user?.totp_enabled || !user?.totp_secret) {
    return badRequest()
  }

  const decrypted = decryptSecret(user.totp_secret)
  if (!decrypted) return badRequest()

  const isValid = totpVerify({ token, secret: decrypted })
  if (!isValid) return badRequest()

  const { error } = await (sb as any)
    .from("dashboard_users")
    .update({ totp_secret: null, totp_enabled: false, recovery_code_hashes: null })
    .eq("uuid", session.sub)

  if (error) return serverError()
  return ok({ success: true })
}
