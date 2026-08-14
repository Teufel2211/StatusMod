"use client"

import { useEffect, useState } from "react"
import { apiFetch, getServerId } from "@/lib/client/auth"

type AuditEntry = {
  id: number
  server_id: string
  who_uuid_hash: string | null
  action: string
  target_uuid_hash: string | null
  detail: string | null
  created_at: string
}

export default function AuditPage() {
  const [logs, setLogs] = useState<AuditEntry[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const serverId = getServerId()
    if (!serverId) {
      setLoading(false)
      return
    }
    apiFetch(`/api/audit/${serverId}`)
      .then((r) => r.json())
      .then((d) => setLogs(Array.isArray(d) ? d : []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display text-[#e8e6e0] mb-1">Audit Log</h1>
        <p className="text-sm text-[#636980]">Track changes and actions on your server</p>
      </div>

      {loading ? (
        <div className="text-sm text-[#636980] font-mono">Loading...</div>
      ) : logs.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-3xl mb-3 opacity-30">◉</div>
          <p className="text-sm text-[#636980]">No audit entries yet</p>
        </div>
      ) : (
        <div className="space-y-2">
          {logs.map((entry) => (
            <div key={entry.id} className="card py-3 px-4 flex items-start gap-3">
              <div className="w-1.5 h-1.5 rounded-full bg-[#d4c892]/40 mt-2 shrink-0" />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <code className="text-xs text-[#d4c892] font-mono">{entry.action}</code>
                  <span className="text-[10px] text-[#636980] font-mono">
                    {new Date(entry.created_at).toLocaleString()}
                  </span>
                </div>
                {entry.detail && (
                  <p className="text-xs text-[#9ea3b3] mt-1 truncate">{entry.detail}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
