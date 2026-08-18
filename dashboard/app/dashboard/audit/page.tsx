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
        <h1 className="text-2xl font-display mb-1" style={{ color: "var(--text-primary)" }}>Audit Log</h1>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>Track changes and actions on your server</p>
      </div>

      {loading ? (
        <div className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>Loading...</div>
      ) : logs.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-3xl mb-3 opacity-30">◉</div>
          <p className="text-sm" style={{ color: "var(--text-muted)" }}>No audit entries yet</p>
        </div>
      ) : (
        <div className="space-y-2">
          {logs.map((entry) => (
            <div key={entry.id} className="card py-3 px-4 flex items-start gap-3">
              <div className="w-1.5 h-1.5 rounded-full mt-2 shrink-0" style={{ backgroundColor: "var(--accent)", opacity: 0.4 }} />
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2">
                  <code className="text-xs font-mono" style={{ color: "var(--accent)" }}>{entry.action}</code>
                  <span className="text-[10px] font-mono" style={{ color: "var(--text-muted)" }}>
                    {new Date(entry.created_at).toLocaleString()}
                  </span>
                </div>
                {entry.detail && (
                  <p className="text-xs mt-1 truncate" style={{ color: "var(--text-secondary)" }}>{entry.detail}</p>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
