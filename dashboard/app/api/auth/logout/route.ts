import { RefreshTokenSchema } from "@/lib/validators"
import { badRequest, unauthorized, ok } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { getRefreshTokenFromCookie, clearRefreshCookieHeader } from "@/lib/cookies"
import crypto from "crypto"

export async function POST(request: Request) {
  let refreshToken = getRefreshTokenFromCookie(request)

  if (!refreshToken) {
    let body: unknown
    try {
      body = await request.json()
    } catch {
      return badRequest()
    }

    const parsed = RefreshTokenSchema.safeParse(body)
    if (!parsed.success) {
      return badRequest()
    }

    refreshToken = parsed.data.refresh_token
  }

  if (!refreshToken) {
    return badRequest()
  }

  const sb = getServiceClient()
  const tokenHash = crypto.createHash("sha256").update(refreshToken).digest("hex")

  await (sb as any)
    .from("refresh_tokens")
    .update({ revoked: true })
    .eq("token_hash", tokenHash)

  const res = ok({ success: true })
  res.headers.set("Set-Cookie", clearRefreshCookieHeader())

  return res
}
