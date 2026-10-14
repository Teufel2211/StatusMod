import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { authorizeData } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"


export const dynamic = "force-dynamic"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const auth = await authorizeData(request, "audit", params.server_id)
  if (!auth) return unauthorized()

  const url = new URL(request.url)
  const rawLimit = parseInt(url.searchParams.get("limit") ?? "50", 10)
  const rawOffset = parseInt(url.searchParams.get("offset") ?? "0", 10)
  const limit = Math.min(Math.max(Number.isFinite(rawLimit) ? rawLimit : 50, 1), 200)
  const offset = Math.max(Number.isFinite(rawOffset) ? rawOffset : 0, 0)
  const sinceRaw = url.searchParams.get("since")
  const since = sinceRaw && !isNaN(Date.parse(sinceRaw)) ? sinceRaw : null

  const sb = getServiceClient()
  let query = (sb as any)
    .from("audit_log")
    .select("*")
    .eq("server_id", params.server_id)
    .order("created_at", { ascending: false })
    .range(offset, offset + limit - 1)

  if (since) {
    query = query.gt("created_at", since)
  }

  const { data, error } = await query

  if (error) return serverError()
  return ok(data ?? [])
}
