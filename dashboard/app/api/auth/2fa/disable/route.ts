import { ok, serverError, unauthorized, tooMany } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { checkRateLimit, RATE_LIMITS } from "@/lib/rate-limit"
import { hashIp } from "@/lib/hash"

export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const rl = checkRateLimit(`totp:${hashIp(ip)}`, RATE_LIMITS.TOTP_VERIFY)
  if (!rl.allowed) {
    return tooMany()
  }

  const sb = getServiceClient()
  const { error } = await (sb as any)
    .from("dashboard_users")
    .update({ totp_secret: null, totp_enabled: false, recovery_code_hashes: null })
    .eq("uuid", session.sub)

  if (error) return serverError()
  return ok({ success: true })
}
