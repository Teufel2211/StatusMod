import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireApiKey } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"


export const dynamic = "force-dynamic"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string; uuid: string } }
) {
  if (!validatePathUuid(params.server_id) || !validatePathUuid(params.uuid)) return badRequest()

  const authed = await requireApiKey(request, "check", params.server_id)
  if (!authed) return unauthorized()

  const { searchParams } = new URL(request.url)
  const sinceRaw = searchParams.get("since")
  const since = sinceRaw && !isNaN(Date.parse(sinceRaw)) ? sinceRaw : null

  const sb = getServiceClient()
  // Hinweis: who_uuid_hash ist gepeppert gespeichert und kann von hier
  // nicht verglichen werden (Pepper kennt nur das Dashboard). Daher alle
  // Admin-Aktionen des Servers liefern; der Mod filtert selbst.
  let query = (sb as any)
    .from("audit_log")
    .select("*")
    .eq("server_id", params.server_id)

  if (since) query = query.gte("created_at", since)
  query = query.order("created_at", { ascending: false }).limit(50)

  const { data, error } = await query
  if (error) return serverError()

  return ok(data ?? [])
}
