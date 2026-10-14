import { verify as totpVerify } from "otplib"
import { decryptSecret, verifyArgon2id } from "./hash"
import { checkRateLimit, RATE_LIMITS } from "./rate-limit"

// Prüft den zweiten Faktor (TOTP oder Recovery-Code) für den Login.
// Gibt ok=false ohne Seiteneffekte zurück, wenn nichts (Gültiges) mitkam.
// Rate-Limit-Key trägt der Aufrufer bei (pro Route/IP getrennt).
export async function verifySecondFactor(
  sb: any,
  userUuid: string,
  rawBody: Record<string, unknown>,
  rateLimitKey: string
): Promise<{ ok: boolean; rateLimited: boolean }> {
  const rl = checkRateLimit(rateLimitKey, RATE_LIMITS.TOTP_VERIFY)
  if (!rl.allowed) return { ok: false, rateLimited: true }

  const { data: user } = await (sb as any)
    .from("dashboard_users")
    .select("totp_secret, recovery_code_hashes")
    .eq("uuid", userUuid)
    .maybeSingle()
  if (!user) return { ok: false, rateLimited: false }

  const recovery = rawBody.recovery_code
  if (typeof recovery === "string" && recovery.length >= 6 && recovery.length <= 128) {
    const hashes: string[] = user.recovery_code_hashes ?? []
    for (let i = 0; i < hashes.length; i++) {
      if (await verifyArgon2id(hashes[i], recovery)) {
        hashes.splice(i, 1)
        await (sb as any)
          .from("dashboard_users")
          .update({ recovery_code_hashes: hashes })
          .eq("uuid", userUuid)
        return { ok: true, rateLimited: false }
      }
    }
    return { ok: false, rateLimited: false }
  }

  const token = rawBody.token
  if (typeof token !== "string" || token.length < 6 || token.length > 12) {
    return { ok: false, rateLimited: false }
  }
  if (!user.totp_secret) return { ok: false, rateLimited: false }
  const secret = decryptSecret(user.totp_secret)
  if (!secret) return { ok: false, rateLimited: false }
  return { ok: totpVerify({ token, secret }), rateLimited: false }
}
