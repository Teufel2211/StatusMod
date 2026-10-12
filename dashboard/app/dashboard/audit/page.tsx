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

function parseDetail(raw: string | null): Record<string, unknown> {
  if (!raw) return {}
  try {
    const v: unknown = JSON.parse(raw)
    if (v && typeof v === "object" && !Array.isArray(v)) return v as Record<string, unknown>
  } catch {}
  return {}
}

function fmtDate(value: unknown): string | null {
  if (typeof value === "number" || typeof value === "string") {
    const d = new Date(value)
    if (!Number.isNaN(d.getTime())) return d.toLocaleString()
  }
  return null
}

function ago(createdAt: string): string {
  const ms = Date.now() - new Date(createdAt).getTime()
  if (Number.isNaN(ms) || ms < 0) return ""
  const min = Math.floor(ms / 60000)
  if (min < 1) return "gerade eben"
  if (min < 60) return `vor ${min} Min.`
  const h = Math.floor(min / 60)
  if (h < 24) return `vor ${h} Std.`
  const d = Math.floor(h / 24)
  if (d < 7) return `vor ${d} Tag${d === 1 ? "" : "en"}`
  return new Date(createdAt).toLocaleDateString()
}

// Jede Aktion als ein Satz, den auch Nicht-Techniker verstehen.
function describe(entry: AuditEntry): string {
  const d = parseDetail(entry.detail)
  const str = (v: unknown) => (typeof v === "string" && v !== "" ? v : null)
  switch (entry.action) {
    case "mute": {
      const mins = typeof d.duration_minutes === "number" ? d.duration_minutes : null
      const until = fmtDate(d.muted_until)
      return `Spieler stummgeschaltet${mins !== null ? ` · ${mins} Min.` : ""}${until ? ` (bis ${until})` : ""}`
    }
    case "unmute":
      return "Stummschaltung aufgehoben"
    case "status_update": {
      const parts: string[] = []
      const status = str(d.status)
      const color = str(d.color)
      if (status) parts.push(`Status „${status}"`)
      if (color) parts.push(`Farbe „${color}"`)
      return parts.length > 0 ? `Spielerstatus geändert: ${parts.join(", ")}` : "Spielerstatus geändert"
    }
    case "preset_upsert": {
      const name = str(d.name)
      return name ? `Preset „${name}" gespeichert` : "Preset gespeichert"
    }
    case "preset_delete": {
      const name = str(d.name)
      return name ? `Preset „${name}" gelöscht` : "Preset gelöscht"
    }
    case "fleet_config_update":
      return "Verteilung aktualisiert"
    case "fleet_config_rollback":
      return "Verteilung zurückgesetzt"
    case "config_update":
      return "Server-Config geändert"
    default:
      return entry.action
  }
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
      <div className="mb-8 fade-up">
        <div className="kicker mb-2">Verlauf</div>
        <h1 className="font-display font-bold text-3xl text-glow" style={{ color: "var(--text-primary)" }}>Aktivitäten</h1>
        <p className="text-sm mt-1" style={{ color: "var(--text-muted)" }}>Was auf deinem Server passiert ist — neueste zuerst</p>
      </div>

      {loading ? (
        <div className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>Loading...</div>
      ) : logs.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-3xl mb-3 opacity-30">◉</div>
          <p className="text-sm" style={{ color: "var(--text-muted)" }}>Noch nichts passiert — Aktionen erscheinen hier automatisch</p>
        </div>
      ) : (
        <div className="space-y-2">
          {logs.map((entry) => (
            <div key={entry.id} className="card py-3 px-4 flex items-start gap-3">
              <div className="w-1.5 h-1.5 rounded-full mt-2 shrink-0" style={{ backgroundColor: "var(--accent)", opacity: 0.4 }} />
              <div className="flex-1 min-w-0">
                <p className="text-sm" style={{ color: "var(--text-primary)" }}>{describe(entry)}</p>
                <span className="text-[10px] font-mono" style={{ color: "var(--text-muted)" }} title={new Date(entry.created_at).toLocaleString()}>
                  {ago(entry.created_at)}
                </span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
