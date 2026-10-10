import { ok, serverError, unauthorized, forbidden, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession, resolveApiKeyServer } from "@/lib/auth"
import { FleetConfigSchema } from "@/lib/validators"
import { writeAuditLog } from "@/lib/audit"

// Fleet config: global dashboard_url + setup_secret pulled by the Fleet mod
// on every server (secret rotation without file access). Reading requires any
// valid server API key (same trust as the player sync); writing requires an
// owner dashboard session.
export async function GET(request: Request) {
  // Mods authenticate with a server API key; the dashboard UI uses an owner JWT.
  const keyServerId = await resolveApiKeyServer(request, "check")
  if (!keyServerId) {
    const session = requireSession(request)
    if (!session || session.role !== "owner") return unauthorized()
  }

  const sb = getServiceClient()
  const { data, error } = await (sb as any)
    .from("fleet_config")
    .select("dashboard_url, setup_secret, updated_at")
    .eq("id", 1)
    .maybeSingle()

  if (error) return serverError()
  return ok({
    dashboard_url: data?.dashboard_url ?? "",
    setup_secret: data?.setup_secret ?? "",
    updated_at: data?.updated_at ?? null,
  })
}

export async function PUT(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()
  if (session.role !== "owner") return forbidden("Nur der Owner kann die Fleet-Config ändern")

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const parsed = FleetConfigSchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const sb = getServiceClient()
  const patch: Record<string, unknown> = { updated_at: new Date().toISOString() }
  if (parsed.data.dashboard_url !== undefined) patch.dashboard_url = parsed.data.dashboard_url
  if (parsed.data.setup_secret !== undefined) patch.setup_secret = parsed.data.setup_secret

  const { error } = await (sb as any)
    .from("fleet_config")
    .update(patch)
    .eq("id", 1)

  if (error) return serverError()

  await writeAuditLog({
    server_id: session.server_id,
    action: "fleet_config_update",
    who_uuid: session.sub,
    details: { keys: Object.keys(patch).filter((k) => k !== "updated_at") },
  })

  return ok({ success: true })
}
