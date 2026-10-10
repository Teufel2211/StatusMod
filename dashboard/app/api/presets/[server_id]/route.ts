import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { authorizeData, requireSession } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"
import { z } from "zod"


export const dynamic = "force-dynamic"

export async function GET(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const authed = await authorizeData(request, "check", params.server_id)
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

const PresetCreateSchema = z.object({
  name: z.string().trim().min(1).max(32),
  status: z.string().max(64),
  color: z.string().max(32),
})

export async function POST(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const session = requireSession(request)
  if (!session) return unauthorized()
  if (session.server_id !== params.server_id) return unauthorized()

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const parsed = PresetCreateSchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const sb = getServiceClient()
  const { error } = await (sb as any).from("custom_presets").upsert(
    {
      name: parsed.data.name.toLowerCase(),
      server_id: params.server_id,
      status: parsed.data.status,
      color: parsed.data.color,
      creator_uuid: session.sub,
    },
    { onConflict: "server_id,name" }
  )

  if (error) return serverError()
  return ok({ success: true })
}
