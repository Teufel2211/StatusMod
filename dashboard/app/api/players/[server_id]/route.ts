import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { authorizeData } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const auth = await authorizeData(request, "check", params.server_id)
  if (!auth) return unauthorized()

  const sb = getServiceClient()
  const { data, error } = await (sb as any)
    .from("players")
    .select("*")
    .eq("server_id", params.server_id)
    .order("created_at", { ascending: false })

  if (error) return serverError()
  return ok(data ?? [])
}
