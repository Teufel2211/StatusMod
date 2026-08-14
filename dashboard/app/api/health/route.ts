import { ok, serverError } from "@/lib/response"
import { getSchemaVersion } from "@/lib/version"
import { getEnv } from "@/lib/env"
import fs from "fs"
import path from "path"

export const dynamic = "force-dynamic"

const JWT_SECRET_PATH = path.join(process.cwd(), ".env.local")

function getJwtAgeDays(): number {
  try {
    const stat = fs.statSync(JWT_SECRET_PATH)
    const ageMs = Date.now() - stat.mtimeMs
    return Math.floor(ageMs / (24 * 60 * 60 * 1000))
  } catch {
    return -1
  }
}

export async function GET() {
  try {
    const env = getEnv()
    const schemaVersion = await getSchemaVersion()
    const jwtAge = getJwtAgeDays()
    const jwtExpired = jwtAge > 90

    const checks = {
      database: "unknown",
      cors: env.CORS_ORIGIN,
      jwt_age_days: jwtAge,
      jwt_rotation_warning: jwtExpired ? "JWT_SECRET is over 90 days old — rotate now" : null,
      schema_version: schemaVersion?.version ?? null,
      schema_name: schemaVersion?.name ?? null,
    }

    const dbOk = schemaVersion !== null
    checks.database = dbOk ? "connected" : "error"

    return ok({
      status: dbOk ? "healthy" : "degraded",
      ...checks,
      timestamp: new Date().toISOString(),
    })
  } catch (err) {
    return serverError()
  }
}
