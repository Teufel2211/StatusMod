import { ok, serverError } from "@/lib/response"
import { getSchemaVersion } from "@/lib/version"
import { getEnv } from "@/lib/env"

export const dynamic = "force-dynamic"

export async function GET() {
  try {
    const env = getEnv()
    const schemaVersion = await getSchemaVersion()

    const dbOk = schemaVersion !== null

    return ok({
      status: dbOk ? "healthy" : "degraded",
      cors: env.CORS_ORIGIN ? "configured" : "missing",
      database: dbOk ? "connected" : "error",
      timestamp: new Date().toISOString(),
    })
  } catch (err) {
    return serverError()
  }
}
