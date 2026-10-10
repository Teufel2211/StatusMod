import { ok, serverError, unauthorized, forbidden } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"

export const dynamic = "force-dynamic"

// Lists fleet config history (newest first) for rollbacks. Secrets are
// never returned — only whether one was set.
export async function GET(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()
  if (session.role !== "owner") return forbidden("Nur der Owner sieht die Fleet-Historie")

  const sb = getServiceClient()
  const { data, error } = await (sb as any)
    .from("fleet_config_history")
    .select("id, dashboard_url, created_at")
    .order("id", { ascending: false })
    .limit(20)

  if (error) return serverError()

  const rows = (Array.isArray(data) ? data : []).map((r: Record<string, unknown>) => ({
    id: r.id,
    dashboard_url: r.dashboard_url ?? "",
    has_secret: true,
    created_at: r.created_at ?? null,
  }))

  return ok(rows)
}
