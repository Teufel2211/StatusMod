import { ok, serverError, unauthorized, forbidden } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"


export const dynamic = "force-dynamic"

// Notfall: alle API-Keys des eigenen Servers sperren (z.B. nach Leak).
// Absichtlich ohne Setup-Code: Der ist nach dem Claim verbraucht und für
// bereits registrierte Server gibt es keinen neuen mehr. Die Owner-Session
// allein ist der Nachweis (stärker als ein 16-Zeichen-Code).
export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) {
    return unauthorized()
  }

  if (session.role !== "owner") {
    return forbidden("Nur der Owner kann alle Keys sperren")
  }

  const sb = getServiceClient()

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
