import { ok, serverError, unauthorized, badRequest } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireApiKey } from "@/lib/auth"
import { validatePathUuid } from "@/lib/validators"
import { z } from "zod"

const PlayerSyncSchema = z.object({
  uuid: z.string().min(1).max(64),
  username: z.string().max(32).optional().nullable(),
  status: z.string().max(64).optional().nullable(),
  color: z.string().max(32).optional().nullable(),
  settings: z.record(z.unknown()).optional().nullable(),
  avatar: z.string().max(160).optional().nullable(),
  online: z.boolean().optional(),
})

const MutedSyncSchema = z.object({
  uuid: z.string().min(1).max(64),
  muted_until: z.number().int().nonnegative(),
})

const BlockedSyncSchema = z.object({
  uuid: z.string().min(1).max(64),
  active: z.boolean().optional().default(true),
})

const SyncBodySchema = z.object({
  players: z.array(PlayerSyncSchema).min(0).max(200),
  muted: z.array(MutedSyncSchema).min(0).max(200).optional().default([]),
  blocked: z.array(BlockedSyncSchema).min(0).max(200).optional().default([]),
})

export async function POST(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const authed = await requireApiKey(request, "check", params.server_id)
  if (!authed) return unauthorized()

  let body: unknown
  try { body = await request.json() } catch { return badRequest() }

  const parsed = SyncBodySchema.safeParse(body)
  if (!parsed.success) return badRequest()

  const sb = getServiceClient()

  const { data: serverExists } = await (sb as any)
    .from("servers")
    .select("id")
    .eq("id", params.server_id)
    .maybeSingle()
  if (!serverExists) return badRequest()

  const nowIso = new Date().toISOString()
  const rows = parsed.data.players.map((p) => {
    const row: Record<string, unknown> = {
      server_id: params.server_id,
      uuid: p.uuid,
      updated_at: nowIso,
      is_online: p.online ?? false,
    }
    if (p.online) row.last_seen = nowIso
    if (p.username != null && p.username !== "") row.username = p.username
    if (p.status != null) row.status = p.status
    if (p.color != null) row.color = p.color
    if (p.settings != null) row.settings = p.settings
    if (p.avatar !== undefined && p.avatar !== null) row.avatar = p.avatar === "" ? null : p.avatar
    return row
  })

  if (rows.length > 0) {
    const { error } = await (sb as any)
      .from("players")
      .upsert(rows, { onConflict: "server_id,uuid" })
    if (error) return serverError()
  }

  const mutedRows = parsed.data.muted
    .filter((m) => m.muted_until > 0)
    .map((m) => ({
      uuid: m.uuid,
      server_id: params.server_id,
      muted_until: new Date(m.muted_until).toISOString(),
    }))
  const mutedDelete = parsed.data.muted.filter((m) => m.muted_until === 0)

  if (mutedRows.length > 0) {
    const { error } = await (sb as any)
      .from("muted_players")
      .upsert(mutedRows, { onConflict: "uuid,server_id" })
    if (error) return serverError()
  }
  if (mutedDelete.length > 0) {
    const uuids = mutedDelete.map((m) => m.uuid)
    const { error } = await (sb as any)
      .from("muted_players")
      .delete()
      .eq("server_id", params.server_id)
      .in("uuid", uuids)
    if (error) return serverError()
  }

  const blockedRows = parsed.data.blocked
    .filter((b) => b.active !== false)
    .map((b) => ({
      uuid: b.uuid,
      server_id: params.server_id,
      blocked_at: new Date().toISOString(),
    }))
  const blockedDelete = parsed.data.blocked.filter((b) => b.active === false)

  if (blockedRows.length > 0) {
    const { error } = await (sb as any)
      .from("blocked_players")
      .upsert(blockedRows, { onConflict: "uuid,server_id" })
    if (error) return serverError()
  }
  if (blockedDelete.length > 0) {
    const uuids = blockedDelete.map((b) => b.uuid)
    const { error } = await (sb as any)
      .from("blocked_players")
      .delete()
      .eq("server_id", params.server_id)
      .in("uuid", uuids)
    if (error) return serverError()
  }

  return ok({ synced: rows.length + mutedRows.length + blockedRows.length })
}

export async function GET(
  request: Request,
  { params }: { params: { server_id: string } }
) {
  if (!validatePathUuid(params.server_id)) return badRequest()

  const authed = await requireApiKey(request, "check", params.server_id)
  if (!authed) return unauthorized()

  const { searchParams } = new URL(request.url)
  const since = searchParams.get("since")

  const sb = getServiceClient()

  let playerQuery = (sb as any)
    .from("players")
    .select("uuid, username, status, color, settings, updated_at")
    .eq("server_id", params.server_id)
  if (since) playerQuery = playerQuery.gte("updated_at", since)
  const { data: players, error: playerError } = await playerQuery
  if (playerError) return serverError()

  let muteQuery = (sb as any)
    .from("muted_players")
    .select("uuid, muted_until")
    .eq("server_id", params.server_id)
  const { data: muted, error: muteError } = await muteQuery
  if (muteError) return serverError()

  let blockQuery = (sb as any)
    .from("blocked_players")
    .select("uuid")
    .eq("server_id", params.server_id)
  const { data: blocked, error: blockError } = await blockQuery
  if (blockError) return serverError()

  let adminQuery = (sb as any)
    .from("audit_log")
    .select("id, action, who_uuid_hash, target_uuid_hash, detail, created_at")
    .eq("server_id", params.server_id)
    .eq("who_uuid_hash", "dashboard")
    .limit(200)
  if (since) adminQuery = adminQuery.gte("created_at", since)
  const { data: adminActions, error: adminError } = await adminQuery
  if (adminError) return serverError()

  return ok({
    players: players ?? [],
    muted: muted ?? [],
    blocked: blocked ?? [],
    admin_actions: adminActions ?? [],
    server_time: new Date().toISOString(),
  })
}
