import { OwnerCodeSchema } from "@/lib/validators"
import { badRequest, unauthorized, serverError, forbidden } from "@/lib/response"
import { NextResponse } from "next/server"
import { getServiceClient } from "@/lib/supabase"
import { hashArgon2id } from "@/lib/hash"
import { checkRateLimit, RATE_LIMITS, getRateLimitHeaders } from "@/lib/rate-limit"
import { resolveApiKeyServer } from "@/lib/auth"

// Registers an owner one-time login code (issued by the mod via
// /status owner-code, printed to the server console only). Mirrors the
// player verify-code flow, bound to the claiming owner's uuid.

export const dynamic = "force-dynamic"

export async function POST(request: Request) {
  const serverId = await resolveApiKeyServer(request, "check")
  if (!serverId) return unauthorized()

  const rl = checkRateLimit(`ownercode:${serverId}`, RATE_LIMITS.CODE_GENERATION)
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

  const { data: server, error: serverError1 } = await (sb as any)
    .from("servers")
    .select("owner_uuid")
    .eq("id", serverId)
    .maybeSingle()

  if (serverError1) return serverError()
  if (!server?.owner_uuid) {
    return forbidden("Server wurde noch nicht über den Setup-Code geclaimt")
  }

  // Delete any existing active owner codes for this server first.
  const { error: deleteError } = await (sb as any)
    .from("owner_login_codes")
    .delete()
    .eq("server_id", serverId)
    .eq("used", false)

  if (deleteError) return serverError()

  const codeHash = await hashArgon2id(code)

  const { error: insertError } = await (sb as any).from("owner_login_codes").insert({
    code_hash: codeHash,
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
