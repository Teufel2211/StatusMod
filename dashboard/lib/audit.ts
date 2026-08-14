import { getServiceClient } from "./supabase"
import { hashAuditUuid } from "./hash"

export async function writeAuditLog(params: {
  server_id: string
  action: string
  who_uuid: string
  target_uuid?: string
  details?: Record<string, unknown>
}) {
  const sb = getServiceClient()
  await (sb as any).from("audit_log").insert({
    server_id: params.server_id,
    action: params.action,
    who_uuid_hash: hashAuditUuid(params.who_uuid),
    target_uuid_hash: params.target_uuid ? hashAuditUuid(params.target_uuid) : null,
    detail: params.details ? JSON.stringify(params.details) : null,
  })
}
