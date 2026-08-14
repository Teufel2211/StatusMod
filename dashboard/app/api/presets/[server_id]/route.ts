import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireApiKey } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const authed = await requireApiKey(request, "check", params.server_id)
  if (!authed) return unauthorized()

  const sb = getServiceClient()
  const { data, error } = await (sb as any)
    .from("custom_presets")
    .select("*")
    .eq("server_id", params.server_id)
    .order("name", { ascending: true })

  if (error) return serverError()
  return ok(data ?? [])
}
