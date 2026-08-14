import { LoginCodeSchema } from "@/lib/validators"
import { badRequest, ok, unauthorized, serverError } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { verifyArgon2id } from "@/lib/hash"

export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) {
    return unauthorized()
  }

  let body: unknown
  try {
    body = await request.json()
  } catch {
    return badRequest()
  }

  const parsed = LoginCodeSchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const { code } = parsed.data

  const sb = getServiceClient()

  const { data: codes, error: codeError } = await (sb as any)
    .from("dashboard_codes")
    .select("*")
    .eq("server_id", session.server_id)
    .is("used", false)
    .gt("expires_at", new Date().toISOString())
    .lt("attempt_count", 3)

  if (codeError || !codes || codes.length === 0) {
    return unauthorized()
  }

  let matchedCode = null
  for (const c of codes) {
    const valid = await verifyArgon2id(c.code_hash, code)
    if (valid) {
      matchedCode = c
      break
    }
  }

  if (!matchedCode) {
    return unauthorized()
  }

  const { error: revokeError } = await (sb as any)
    .from("api_keys")
    .update({ revoked: true })
    .eq("server_id", session.server_id)
    .is("revoked", false)

  if (revokeError) {
    return serverError()
  }

  return ok({ success: true })
}
