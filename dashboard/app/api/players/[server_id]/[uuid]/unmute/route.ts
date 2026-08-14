import { ok, serverError, unauthorized, forbidden, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"
import { writeAuditLog } from "@/lib/audit"

export async function POST(
  request: Request,
  { params }: { params: { server_id: string; uuid: string } }
) {
  if (!validatePathUuid(params.server_id) || !validatePathUuid(params.uuid)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.server_id !== params.server_id) return unauthorized()
  if (session.role !== "owner" && session.role !== "admin") return forbidden("Nur Owner/Admin können entmuten")

  const sb = getServiceClient()
  const { error } = await (sb as any)
    .from("muted_players")
    .delete()
    .eq("uuid", params.uuid)
    .eq("server_id", params.server_id)

  if (error) return serverError()

  await writeAuditLog({
    server_id: params.server_id,
    action: "unmute",
    who_uuid: session.sub,
    target_uuid: params.uuid,
  })

  return ok({ success: true })
}
