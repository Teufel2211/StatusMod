import { badRequest, unauthorized, serverError, ok } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { getEnv } from "@/lib/env"
import { decryptSecret } from "@/lib/hash"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders } from "@/lib/rate-limit"
import { validatePathUuid } from "@/lib/validators"
import { NextResponse } from "next/server"

export async function GET(request: Request) {
  const env = getEnv()
  const expectedSecret = env.MOD_SETUP_SECRET
  if (!expectedSecret) return unauthorized()

  const auth = request.headers.get("authorization") ?? ""
  if (auth !== `Bearer ${expectedSecret}`) return unauthorized()

  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const rl = checkRateLimit(`setup_pending:${ip}`, RATE_LIMITS.CODE_GENERATION)
  if (!rl.allowed) {
    return NextResponse.json({ error: "Ungültige Anfrage" }, { status: 429, headers: getRateLimitHeaders(rl) })
  }

  const url = new URL(request.url)
  const serverId = url.searchParams.get("server_id") ?? ""
  if (!validatePathUuid(serverId)) return badRequest()

  const sb = getServiceClient()

  const { data: pending, error: pendingError } = await (sb as any)
    .from("setup_pending_keys")
    .select("key_encrypted")
    .eq("server_id", serverId)
    .maybeSingle()

  if (pendingError) return serverError()

  if (!pending) {
    return ok({ pending: false })
  }

  const apiKey = decryptSecret(pending.key_encrypted)
  if (!apiKey) return serverError()

  const { error: deleteError } = await (sb as any).from("setup_pending_keys").delete().eq("server_id", serverId)
  if (deleteError) return serverError()

  return ok({ pending: true, api_key: apiKey })
}
