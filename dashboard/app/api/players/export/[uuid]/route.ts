import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"

export async function GET(
  request: Request,
  { params }: { params: { uuid: string } }
) {
  if (!validatePathUuid(params.uuid)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  const sb = getServiceClient()
  const serverId = session.server_id

  const [players, audit] = await Promise.all([
    (sb as any).from("players").select("*").eq("uuid", params.uuid).eq("server_id", serverId),
    (sb as any).from("audit_log").select("*").eq("who_uuid_hash", params.uuid).eq("server_id", serverId),
  ])

  return ok({
    player_data: players.data ?? [],
    audit_log: audit.data ?? [],
    exported_at: new Date().toISOString(),
  })
}
