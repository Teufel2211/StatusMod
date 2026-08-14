import { badRequest, ok, unauthorized, forbidden, serverError } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"

export async function DELETE(
  request: Request,
  { params }: { params: { id: string } }
) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.role !== "owner") return forbidden("Nur der Owner kann API-Keys widerrufen")

  const id = parseInt(params.id, 10)
  if (isNaN(id) || id < 1) return badRequest()

  const sb = getServiceClient()

  const { data: keyData, error: findError } = await (sb as any)
    .from("api_keys")
    .select("id")
    .eq("id", id)
    .eq("server_id", session.server_id)
    .maybeSingle()

  if (findError || !keyData) return unauthorized()

  const { error } = await (sb as any)
    .from("api_keys")
    .update({ revoked: true })
    .eq("id", id)

  if (error) return serverError()

  return ok({ success: true })
}
