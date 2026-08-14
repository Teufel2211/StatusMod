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

  const { searchParams } = new URL(request.url)
  const since = searchParams.get("since")

  const sb = getServiceClient()
  let query = (sb as any)
    .from("audit_log")
    .select("*")
    .eq("server_id", params.server_id)
    .eq("who_uuid_hash", params.uuid)

  if (since) query = query.gte("created_at", since)
  query = query.order("created_at", { ascending: false }).limit(50)

  const { data, error } = await query
  if (error) return serverError()

  return ok(data ?? [])
}
