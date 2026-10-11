import { ok, serverError, unauthorized, forbidden, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"
import { writeAuditLog } from "@/lib/audit"


export const dynamic = "force-dynamic"

export async function DELETE(
  request: Request,
  { params }: { params: { server_id: string; name: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.server_id !== params.server_id) return unauthorized()
  if (session.role !== "owner") return forbidden("Nur der Owner kann Presets löschen")

  const sb = getServiceClient()
  const { error } = await (sb as any)
    .from("custom_presets")
    .delete()
    .eq("name", params.name)
    .eq("server_id", params.server_id)

  if (error) return serverError()

  await writeAuditLog({
    server_id: params.server_id,
    action: "preset_delete",
    who_uuid: session.sub,
    details: { name: params.name },
  })

  return ok({ success: true })
}
