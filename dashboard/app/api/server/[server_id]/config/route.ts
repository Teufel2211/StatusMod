import { ok, serverError, unauthorized, forbidden, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { authorizeData, requireSession } from "@/lib/auth"
import { validatePathUuid, ServerConfigSchema } from "@/lib/validators"
import { writeAuditLog } from "@/lib/audit"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const auth = await authorizeData(request, "check", params.server_id)
  if (!auth) return unauthorized()

  const sb = getServiceClient()
  const { data, error } = await (sb as any)
    .from("servers")
    .select("config")
    .eq("id", params.server_id)
    .maybeSingle()

  if (error) return serverError()
  return ok(data?.config ?? {})
}

export async function PUT(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.server_id !== params.server_id) return unauthorized()
  if (session.role !== "owner") return forbidden("Nur der Owner kann die Config ändern")

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const parsed = ServerConfigSchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const sb = getServiceClient()
  const { error } = await (sb as any)
    .from("servers")
    .update({ config: parsed.data.config })
    .eq("id", params.server_id)

  if (error) return serverError()

  await writeAuditLog({
    server_id: params.server_id,
    action: "config_update",
    who_uuid: session.sub,
    details: { keys: Object.keys(parsed.data.config as Record<string, unknown>) },
  })

  return ok({ success: true })
}
