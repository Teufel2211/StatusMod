import { badRequest, ok, serverError, unauthorized } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"

export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const { discord_id } = body as Record<string, unknown>
  if (typeof discord_id !== "string") return badRequest()

  const sb = getServiceClient()

  const { data: existing } = await (sb as any)
    .from("discord_links")
    .select("id")
    .eq("discord_id", discord_id)
    .maybeSingle()

  if (existing) return badRequest()

  const { error } = await (sb as any)
    .from("discord_links")
    .insert({
      discord_id,
      uuid: session.sub,
      server_id: session.server_id,
      role: session.role,
      linked_at: new Date().toISOString(),
    })

  if (error) return serverError()
  return ok({ success: true })
}
