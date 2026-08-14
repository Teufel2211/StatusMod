import { ok, serverError, unauthorized } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"
import { encryptSecret, hashArgon2id } from "@/lib/hash"
import { generateSecret } from "otplib"
import crypto from "crypto"

export async function POST(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  const secret = generateSecret()

  const otpauth = `otpauth://totp/StatusMod:${encodeURIComponent(session.sub)}?secret=${secret}&issuer=StatusMod&algorithm=SHA1&digits=6&period=30`

  const encryptedSecret = encryptSecret(secret)

  const recoveryCodes = Array.from({ length: 10 }, () => crypto.randomBytes(4).toString("hex"))
  const recoveryCodeHashes = await Promise.all(recoveryCodes.map((rc) => hashArgon2id(rc)))

  const sb = getServiceClient()
  const { error } = await (sb as any)
    .from("dashboard_users")
    .update({
      totp_secret: encryptedSecret,
      recovery_code_hashes: recoveryCodeHashes,
    })
    .eq("uuid", session.sub)

  if (error) return serverError()

  return ok({
    secret,
    otpauth_url: otpauth,
    recovery_codes: recoveryCodes,
  })
}
