import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"

export async function DELETE(
  request: Request,
  { params }: { params: { server_id: string; name: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.server_id !== params.server_id) return unauthorized()

  const sb = getServiceClient()
  const { error } = await (sb as any)
    .from("custom_presets")
    .delete()
    .eq("name", params.name)
    .eq("server_id", params.server_id)

  if (error) return serverError()
  return ok({ success: true })
}
