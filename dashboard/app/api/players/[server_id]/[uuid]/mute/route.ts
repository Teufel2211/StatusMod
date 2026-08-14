import { ok, serverError, unauthorized, forbidden, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { validatePathUuid, MutePlayerSchema } from "@/lib/validators"
import { writeAuditLog } from "@/lib/audit"

export async function POST(
  request: Request,
  { params }: { params: { server_id: string; uuid: string } }
) {
  if (!validatePathUuid(params.server_id) || !validatePathUuid(params.uuid)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.server_id !== params.server_id) return unauthorized()
  if (session.role !== "owner" && session.role !== "admin") return forbidden("Nur Owner/Admin können muten")

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const parsed = MutePlayerSchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const sb = getServiceClient()
  const mutedUntil = new Date(Date.now() + parsed.data.duration_minutes * 60 * 1000).toISOString()

  const { error } = await (sb as any)
    .from("muted_players")
    .upsert(
      { uuid: params.uuid, server_id: params.server_id, muted_until: mutedUntil },
      { onConflict: "uuid,server_id" }
    )

  if (error) return serverError()

  await writeAuditLog({
    server_id: params.server_id,
    action: "mute",
    who_uuid: session.sub,
    target_uuid: params.uuid,
    details: { duration_minutes: parsed.data.duration_minutes, muted_until: mutedUntil },
  })

  return ok({ muted_until: mutedUntil })
}
