import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireApiKey } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string; uuid: string } }
) {
  if (!validatePathUuid(params.server_id) || !validatePathUuid(params.uuid)) return badRequest()

  const authed = await requireApiKey(request, "check", params.server_id)
  if (!authed) return unauthorized()

  const sb = getServiceClient()

  const { data: blocked, error: blockError } = await (sb as any)
    .from("blocked_players")
    .select("blocked_at")
    .eq("uuid", params.uuid)
    .eq("server_id", params.server_id)
    .maybeSingle()

  if (blockError) return serverError()

  const { data: muted, error: muteError } = await (sb as any)
    .from("muted_players")
    .select("muted_until")
    .eq("uuid", params.uuid)
    .eq("server_id", params.server_id)
    .maybeSingle()

  if (muteError) return serverError()

  const now = new Date().toISOString()
  const isMuted = muted && muted.muted_until > now

  return ok({
    blocked: !!blocked,
    blocked_at: blocked?.blocked_at ?? null,
    muted: !!isMuted,
    muted_until: isMuted ? muted.muted_until : null,
  })
}
