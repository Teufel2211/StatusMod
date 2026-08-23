import { LoginCodeSchema } from "@/lib/validators"
import { badRequest, unauthorized, serverError, forbidden } from "@/lib/response"
import { NextResponse } from "next/server"
import { getServiceClient } from "@/lib/supabase"
import { hashArgon2id } from "@/lib/hash"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders } from "@/lib/rate-limit"
import { resolveApiKeyServer } from "@/lib/auth"

export async function POST(request: Request) {
  const serverId = await resolveApiKeyServer(request, "check")
  if (!serverId) return unauthorized()

  const rl = checkRateLimit(`verifycode:${serverId}`, RATE_LIMITS.CODE_GENERATION)
  if (!rl.allowed) {
    return NextResponse.json({ error: "Ungültige Anfrage" }, { status: 429, headers: getRateLimitHeaders(rl) })
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return badRequest()
  }

  const parsed = LoginCodeSchema.safeParse(body)
  if (!parsed.success) {
    return badRequest()
  }

  const { code } = parsed.data

  const sb = getServiceClient()

  const { data: server, error: serverError1 } = await (sb as any)
    .from("servers")
    .select("owner_uuid")
    .eq("id", serverId)
    .maybeSingle()

  if (serverError1) return serverError()
  if (!server?.owner_uuid) {
    return forbidden("Server wurde noch nicht über den Setup-Code geclaimt")
  }

  // Delete any existing active codes for this server first (prevents code accumulation)
  const { error: deleteError } = await (sb as any)
    .from("verify_codes")
    .delete()
    .eq("server_id", serverId)
    .eq("used", false)

  if (deleteError) return serverError()

  const codeHash = await hashArgon2id(code)

  const { error: insertError } = await (sb as any).from("verify_codes").insert({
    code_hash: codeHash,
    uuid: server.owner_uuid,
    server_id: serverId,
    expires_at: new Date(Date.now() + 10 * 60 * 1000).toISOString(),
    used: false,
    attempt_count: 0,
  })

  if (insertError) return serverError()

  return NextResponse.json(
    { expires_in: 600 },
    { headers: getRateLimitHeaders(rl) }
  )
}
