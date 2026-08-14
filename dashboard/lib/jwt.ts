import jwt from "jsonwebtoken"
import { getEnv } from "./env"

export interface JwtPayload {
  sub: string
  server_id: string
  role: "owner" | "admin" | "viewer"
  iat: number
  exp: number
}

export function signJwt(payload: { sub: string; server_id: string; role: JwtPayload["role"] }): string {
  const env = getEnv()
  return jwt.sign(payload, env.JWT_SECRET, {
    algorithm: "HS256",
    expiresIn: "15m",
  })
}

export function verifyJwt(token: string): JwtPayload | null {
  const env = getEnv()
  try {
    const decoded = jwt.verify(token, env.JWT_SECRET, {
      algorithms: ["HS256"],
    }) as JwtPayload
    return decoded
  } catch {
    if (!env.JWT_SECRET_PREVIOUS) return null
    try {
      const decoded = jwt.verify(token, env.JWT_SECRET_PREVIOUS, {
        algorithms: ["HS256"],
      }) as JwtPayload
      return decoded
    } catch {
      return null
    }
  }
}
