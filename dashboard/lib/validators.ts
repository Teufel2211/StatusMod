import { z } from "zod"

const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/

export const SetupCodeSchema = z.object({
  code: z.string().length(16).regex(/^[A-Za-z0-9]+$/),
})

export const SetupInitSchema = z.object({
  server_id: z.string().regex(uuidRegex),
  code: z.string().length(16).regex(/^[A-Za-z0-9]+$/),
})

export const LoginCodeSchema = z.object({
  code: z.string().length(8).regex(/^[A-Za-z0-9]+$/),
})

export const RefreshTokenSchema = z.object({
  refresh_token: z.string().min(1),
})

export const ApiKeyCreateSchema = z.object({
  scopes: z.array(z.enum(["check", "audit", "*"])).min(1).max(5),
})

export const PlayerStatusFieldSchema = z.object({
  status: z.string().max(64).optional(),
  color: z.string().max(32).optional(),
})

export const MutePlayerSchema = z.object({
  duration_minutes: z.number().int().positive().max(525600),
})

export const ServerConfigSchema = z.object({
  config: z.record(z.unknown()).refine((v) => JSON.stringify(v).length < 102400, {
    message: "Config too large (max 100KB)",
  }),
})

export function validatePathUuid(value: string): boolean {
  return uuidRegex.test(value)
}
