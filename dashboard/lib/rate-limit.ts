const stores = new Map<string, { count: number; resetAt: number }>()

export interface RateLimitConfig {
  windowMs: number
  max: number
}

export function checkRateLimit(key: string, config: RateLimitConfig): { allowed: boolean; remaining: number; resetAt: number } {
  const now = Date.now()
  const entry = stores.get(key)

  if (!entry || now > entry.resetAt) {
    stores.set(key, { count: 1, resetAt: now + config.windowMs })
    return { allowed: true, remaining: config.max - 1, resetAt: now + config.windowMs }
  }

  entry.count++
  if (entry.count > config.max) {
    return { allowed: false, remaining: 0, resetAt: entry.resetAt }
  }

  return { allowed: true, remaining: config.max - entry.count, resetAt: entry.resetAt }
}

export function getRateLimitHeaders(result: { allowed: boolean; remaining: number; resetAt: number }) {
  return {
    "X-RateLimit-Limit": String(result.remaining),
    "X-RateLimit-Reset": String(Math.ceil(result.resetAt / 1000)),
  }
}

export const RATE_LIMITS = {
  LOGIN: { windowMs: 60_000, max: 3 },
  CODE_GENERATION: { windowMs: 60_000, max: 3 },
  TOTP_VERIFY: { windowMs: 60_000, max: 5 },
} as const

const LOGIN_LOCKOUT = {
  maxFailures: 3,
  windowMs: 60_000,
  banMs: 300_000,
} as const

const failureStore = new Map<string, { count: number; firstAt: number }>()
const bannedStore = new Map<string, number>()

export function isLockedOut(key: string): boolean {
  const until = bannedStore.get(key)
  if (!until) return false
  if (Date.now() > until) {
    bannedStore.delete(key)
    return false
  }
  return true
}

export function lockoutRemainingMs(key: string): number {
  const until = bannedStore.get(key)
  if (!until) return 0
  const remaining = until - Date.now()
  if (remaining <= 0) {
    bannedStore.delete(key)
    return 0
  }
  return remaining
}

export function recordLoginFailure(key: string): void {
  const now = Date.now()
  const f = failureStore.get(key)
  if (!f || now > f.firstAt + LOGIN_LOCKOUT.windowMs) {
    failureStore.set(key, { count: 1, firstAt: now })
    return
  }
  f.count++
  if (f.count >= LOGIN_LOCKOUT.maxFailures) {
    bannedStore.set(key, now + LOGIN_LOCKOUT.banMs)
    failureStore.delete(key)
  }
}

export function clearLoginFailures(key: string): void {
  failureStore.delete(key)
  bannedStore.delete(key)
}
