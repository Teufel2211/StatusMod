import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { authorizeData, requireSession } from "@/lib/auth"
import { validatePathUuid, PlayerStatusFieldSchema } from "@/lib/validators"
import { hmacUuid } from "@/lib/hash"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string; uuid: string } }
) {
  if (!validatePathUuid(params.server_id) || !validatePathUuid(params.uuid)) return badRequest()

  const auth = await authorizeData(request, "check", params.server_id)
  if (!auth) return unauthorized()

  const sb = getServiceClient()
  const { data, error } = await (sb as any)
    .from("players")
    .select("*")
    .eq("server_id", params.server_id)
    .eq("uuid", params.uuid)
    .maybeSingle()

  if (error) return serverError()
  return ok(data ?? null)
}

export async function PUT(
  request: Request,
  { params }: { params: { server_id: string; uuid: string } }
) {
  if (!validatePathUuid(params.server_id) || !validatePathUuid(params.uuid)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.server_id !== params.server_id) return unauthorized()

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const parsed = PlayerStatusFieldSchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const sb = getServiceClient()

  const { data: existing, error: findError } = await (sb as any)
    .from("players")
    .select("id")
    .eq("server_id", params.server_id)
    .eq("uuid", params.uuid)
    .maybeSingle()

  if (findError) return serverError()

  const updateData: Record<string, unknown> = {}
  if (parsed.data.status !== undefined) updateData.status = parsed.data.status
  if (parsed.data.color !== undefined) updateData.color = parsed.data.color

  if (existing) {
    const { error } = await (sb as any)
      .from("players")
      .update(updateData)
      .eq("id", existing.id)
    if (error) return serverError()
  } else {
    const { error } = await (sb as any)
      .from("players")
      .insert({ server_id: params.server_id, uuid: params.uuid, ...updateData })
    if (error) return serverError()
  }

  return ok({ success: true })
}

export async function DELETE(
  request: Request,
  { params }: { params: { server_id: string; uuid: string } }
) {
  if (!validatePathUuid(params.server_id) || !validatePathUuid(params.uuid)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()

  if (session.server_id !== params.server_id) return unauthorized()
  if (session.sub !== params.uuid) return unauthorized()

  const sb = getServiceClient()
  const now = new Date().toISOString()

  const { data: server } = await (sb as any)
    .from("servers")
    .select("server_secret")
    .eq("id", params.server_id)
    .maybeSingle()

  if (!server?.server_secret) return serverError()

  const serverSecret = server.server_secret as string
  const anonymizedUuid = hmacUuid(params.uuid, serverSecret)

  const { error: playerError } = await (sb as any)
    .from("players")
    .update({
      anonymized: true,
      username: null,
      status: null,
      color: null,
      settings: null,
      uuid: anonymizedUuid,
    })
    .eq("uuid", params.uuid)

  if (playerError) return serverError()

  await Promise.all([
    (sb as any).from("blocked_players").delete().eq("uuid", params.uuid),
    (sb as any).from("muted_players").update({ muted_until: now }).eq("uuid", params.uuid),
  ])

  return ok({ success: true, message: "Data anonymized. Full deletion in 24h." })
}
