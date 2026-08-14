const REFRESH_COOKIE = "refresh_token"
const REFRESH_TTL_SECONDS = 7 * 24 * 60 * 60

function secureFlag(): string {
  return process.env.ALLOW_HTTP === "true" ? "" : "; Secure"
}

export function getRefreshTokenFromCookie(request: Request): string | null {
  const cookieHeader = request.headers.get("cookie") ?? ""
  for (const part of cookieHeader.split(";")) {
    const idx = part.indexOf("=")
    if (idx < 0) continue
    const key = part.slice(0, idx).trim()
    if (key === REFRESH_COOKIE) {
      const raw = part.slice(idx + 1).trim()
      if (!raw) return null
      try {
        return decodeURIComponent(raw)
      } catch {
        return raw
      }
    }
  }
  return null
}

export function refreshCookieHeader(raw: string): string {
  return `${REFRESH_COOKIE}=${raw}; HttpOnly${secureFlag()}; SameSite=Strict; Path=/; Max-Age=${REFRESH_TTL_SECONDS}`
}

export function clearRefreshCookieHeader(): string {
  return `${REFRESH_COOKIE}=; HttpOnly${secureFlag()}; SameSite=Strict; Path=/; Max-Age=0`
}
