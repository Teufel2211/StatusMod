import { ok, serverError, unauthorized, forbidden, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession, resolveApiKeyServer } from "@/lib/auth"
import { FleetConfigSchema } from "@/lib/validators"
import { writeAuditLog } from "@/lib/audit"

// Fleet config: global dashboard_url + setup_secret pulled by the Fleet mod
// on every server (secret rotation without file access). Reading requires any
// valid server API key (same trust as the player sync); writing requires an
// owner dashboard session.

export const dynamic = "force-dynamic"

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

  let dashboardUrl = parsed.data.dashboard_url
  let setupSecret = parsed.data.setup_secret
  let restoredFrom: number | null = null

  // Rollback: Werte aus einem Historien-Eintrag übernehmen.
  if (parsed.data.restore_id !== undefined) {
    const sb = getServiceClient()
    const { data: hist, error: histError } = await (sb as any)
      .from("fleet_config_history")
      .select("dashboard_url, setup_secret")
      .eq("id", parsed.data.restore_id)
      .maybeSingle()
    if (histError || !hist) return badRequest()
    dashboardUrl = typeof hist.dashboard_url === "string" ? hist.dashboard_url : ""
    setupSecret = typeof hist.setup_secret === "string" ? hist.setup_secret : ""
    restoredFrom = parsed.data.restore_id
  }

  const sb = getServiceClient()
  const patch: Record<string, unknown> = { updated_at: new Date().toISOString() }
  if (dashboardUrl !== undefined) patch.dashboard_url = dashboardUrl
  if (setupSecret !== undefined) patch.setup_secret = setupSecret

  const { error } = await (sb as any)
    .from("fleet_config")
    .update(patch)
    .eq("id", 1)

  if (error) return serverError()

  // Historie für Rollbacks (nur die tatsächlich gesetzten Werte).
  await (sb as any).from("fleet_config_history").insert({
    dashboard_url: typeof patch.dashboard_url === "string" ? patch.dashboard_url : "",
    setup_secret: typeof patch.setup_secret === "string" ? patch.setup_secret : "",
    by_uuid: session.sub,
  })

  await writeAuditLog({
    server_id: session.server_id,
    action: restoredFrom !== null ? "fleet_config_rollback" : "fleet_config_update",
    who_uuid: session.sub,
    details: restoredFrom !== null
      ? { restored_from: restoredFrom }
      : { keys: Object.keys(patch).filter((k) => k !== "updated_at") },
  })

  return ok({ success: true })
}
