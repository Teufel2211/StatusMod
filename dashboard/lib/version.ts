import { getServiceClient } from "./supabase"

interface Migration {
  version: number
  name: string
  applied_at: string
  checksum: string
}

export async function getSchemaVersion(): Promise<Migration | null> {
  try {
    const sb = getServiceClient()
    const { data } = await (sb as any)
      .from("schema_version")
      .select("*")
      .order("version", { ascending: false })
      .limit(1)
      .maybeSingle()
    return data ?? null
  } catch {
    return null
  }
}
