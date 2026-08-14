import { SetupInitSchema } from "@/lib/validators"
import { badRequest, unauthorized, serverError, ok } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { getEnv } from "@/lib/env"
import { hashArgon2id } from "@/lib/hash"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders } from "@/lib/rate-limit"
import { NextResponse } from "next/server"

export async function POST(request: Request) {
  const env = getEnv()
  const expectedSecret = env.MOD_SETUP_SECRET
  if (!expectedSecret) return unauthorized()

  const auth = request.headers.get("authorization") ?? ""
  if (auth !== `Bearer ${expectedSecret}`) return unauthorized()

  const ip = request.headers.get("x-forwarded-for") ?? "unknown"
  const rl = checkRateLimit(`setup_init:${ip}`, RATE_LIMITS.CODE_GENERATION)
  if (!rl.allowed) {
    return NextResponse.json({ error: "Ungültige Anfrage" }, { status: 429, headers: getRateLimitHeaders(rl) })
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return badRequest()
  }

  const parsed = SetupInitSchema.safeParse(body)
  if (!parsed.success) {
    return badRequest()
  }

  const { server_id, code } = parsed.data
  const sb = getServiceClient()

  const { data: existing, error: existingError } = await (sb as any)
    .from("servers")
    .select("id")
    .eq("id", server_id)
    .maybeSingle()

  if (existingError) return serverError()

  if (!existing) {
    const { error: insertServerError } = await (sb as any).from("servers").insert({
      id: server_id,
      config: {},
    })
    if (insertServerError) return serverError()
  }

  const codeHash = await hashArgon2id(code)

  const { error: insertCodeError } = await (sb as any).from("dashboard_codes").insert({
    code_hash: codeHash,
    server_id,
    expires_at: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
    used: false,
  })

  if (insertCodeError) return serverError()

  return ok({ server_id, expires_in: 86400 })
}
