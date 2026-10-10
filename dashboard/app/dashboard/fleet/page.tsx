"use client"

import { useEffect, useState } from "react"
import { apiFetch, getServerId } from "@/lib/client/auth"
import { useRealtimePlayers } from "@/lib/client/use-realtime"
import { PlayerAvatar } from "@/components/player-avatar"

type HealthData = {
  status: string
  database: string
  timestamp: string
}

type AuditEntry = {
  id: number
  server_id: string
  action: string
  detail: string | null
  created_at: string
}

function ageString(iso: string | null): string {
  if (!iso) return "—"
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return `vor ${s}s`
  const m = Math.floor(s / 60)
  if (m < 60) return `vor ${m} Min`
  const h = Math.floor(m / 60)
  if (h < 48) return `vor ${h} Std`
  return `vor ${Math.floor(h / 24)} Tg`
}

function actionColor(action: string): string {
  const a = action.toLowerCase()
  if (a.includes("mute") || a.includes("block") || a.includes("ban")) return "#f87171"
  if (a.includes("fleet") || a.includes("config") || a.includes("key")) return "#34d399"
  if (a.includes("login") || a.includes("auth") || a.includes("code")) return "#fbbf24"
  return "var(--accent)"
}

export default function FleetAdminPage() {
  const [health, setHealth] = useState<HealthData | null>(null)
  const [knownCount, setKnownCount] = useState<number | null>(null)
  const [activity, setActivity] = useState<AuditEntry[]>([])
  const [fleetUrl, setFleetUrl] = useState("")
  const [fleetSecret, setFleetSecret] = useState("")
  const [fleetUpdatedAt, setFleetUpdatedAt] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState("")
  const [loaded, setLoaded] = useState(false)
  const [serverId, setServerId] = useState("")
  const { players: livePlayers, connection } = useRealtimePlayers()

  useEffect(() => {
    fetch("/api/health")
      .then((r) => r.json())
      .then((d) => setHealth(d))
      .catch(() => {})
    const sid = getServerId()
    if (sid) {
      setServerId(sid)
      apiFetch(`/api/players/${sid}`)
        .then((r) => (r.ok ? r.json() : []))
        .then((d) => setKnownCount(Array.isArray(d) ? d.length : null))
        .catch(() => {})
      apiFetch(`/api/audit/${sid}`)
        .then((r) => (r.ok ? r.json() : []))
        .then((d) => setActivity(Array.isArray(d) ? d.slice(0, 8) : []))
        .catch(() => {})
    }
    apiFetch(`/api/fleet/config`)
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!d) {
          setMessage("Kein Zugriff (Owner erforderlich).")
          return
        }
        if (typeof d.dashboard_url === "string") setFleetUrl(d.dashboard_url)
        if (typeof d.setup_secret === "string") setFleetSecret(d.setup_secret)
        if (typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
        setLoaded(true)
      })
      .catch(() => setMessage("Laden fehlgeschlagen."))
  }, [])

  async function handleSave() {
    setSaving(true)
    setMessage("")

    try {
      const res = await apiFetch(`/api/fleet/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dashboard_url: fleetUrl, setup_secret: fleetSecret }),
      })

      if (res.ok) {
        setMessage("Fleet config gespeichert.")
        apiFetch(`/api/fleet/config`)
          .then((r) => (r.ok ? r.json() : null))
          .then((d) => {
            if (d && typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
          })
          .catch(() => {})
      } else setMessage("Speichern fehlgeschlagen.")
    } catch {
      setMessage("Speichern fehlgeschlagen.")
    } finally {
      setSaving(false)
    }
  }

  const fleetActive = fleetSecret !== "" || fleetUrl !== ""
  const stats = [
    {
      label: "Fleet",
      value: fleetActive ? "Aktiv" : "Aus",
      badge: fleetActive ? "badge-green" : "badge-yellow",
      sub: fleetUpdatedAt ? `Update ${ageString(fleetUpdatedAt)}` : "noch nie gesetzt",
    },
    {
      label: "Server",
      value: health?.status === "healthy" ? "Online" : (health?.status ?? "—"),
      badge: health?.status === "healthy" ? "badge-green" : "badge-yellow",
      sub: serverId ? `ID ${serverId.slice(0, 8)}…` : "kein Server",
    },
    {
      label: "Spieler online",
      value: String(livePlayers.length),
      badge: livePlayers.length > 0 ? "badge-green" : "",
      sub: knownCount !== null ? `${knownCount} bekannt` : "…",
    },
    {
      label: "Realtime",
      value: connection === "connected" ? "Live" : connection === "connecting" ? "Verbinden…" : "Offline",
      badge: connection === "connected" ? "badge-green" : connection === "connecting" ? "badge-yellow" : "badge-red",
      sub: "SSE-Feed",
    },
  ]

  return (
    <div>
      <div className="mb-8 flex items-start justify-between gap-4 flex-wrap fade-up">
        <div>
          <div className="kicker mb-2">Zentrale Steuerung · alle Server</div>
          <h1 className="font-display font-bold text-3xl text-glow" style={{ color: "var(--text-primary)" }}>Fleet <span className="text-gradient">Admin</span></h1>
          <p className="text-sm mt-1" style={{ color: "var(--text-muted)" }}>
            Zentrale Steuerung für alle Server mit Fleet-Mod
          </p>
        </div>
        <span className={connection === "connected" ? "badge-green" : "badge-yellow"}>
          {connection === "connected" ? "● Live" : "● " + connection}
        </span>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {stats.map((s) => (
          <div key={s.label} className="card">
            <div className="text-xs font-medium mb-2" style={{ color: "var(--text-muted)" }}>{s.label}</div>
            <div className="flex items-center gap-2">
              <span className="text-lg font-semibold" style={{ color: "var(--text-primary)" }}>{s.value}</span>
              {s.badge === "badge-green" && <span className="badge-green">OK</span>}
              {s.badge === "badge-yellow" && <span className="badge-yellow">?</span>}
              {s.badge === "badge-red" && <span className="badge-red">!</span>}
            </div>
            <div className="text-[11px] font-mono mt-2" style={{ color: "var(--text-muted)" }}>{s.sub}</div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-8">
        <div className="lg:col-span-2 space-y-4">
          <div className="card">
            <h2 className="text-sm font-semibold mb-3" style={{ color: "var(--text-primary)" }}>
              Live Spieler ({livePlayers.length})
            </h2>
            {livePlayers.length === 0 ? (
              <p className="text-sm" style={{ color: "var(--text-muted)" }}>Niemand online.</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {livePlayers.map((p) => (
                  <a
                    key={p.uuid}
                    href={`/dashboard/players/${p.uuid}`}
                    className="flex items-center gap-2 px-3 py-2 rounded-lg transition-colors text-sm"
                    style={{ border: "1px solid var(--border)" }}
                    onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = "var(--bg-hover)" }}
                    onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = "" }}
                  >
                    <PlayerAvatar uuid={p.uuid} username={p.username} avatar={p.avatar} sizePx={20} />
                    <span style={{ color: "var(--text-primary)" }}>{p.username ?? "Unknown"}</span>
                    {p.status && (
                      <span className="text-xs" style={{ color: "var(--text-muted)" }}>{p.status}</span>
                    )}
                  </a>
                ))}
              </div>
            )}
          </div>

          <div className="card">
            <div className="flex items-center justify-between mb-3">
              <h2 className="text-sm font-semibold" style={{ color: "var(--text-primary)" }}>Letzte Aktivität</h2>
              <a href="/dashboard/audit" className="text-xs underline underline-offset-2" style={{ color: "var(--text-muted)" }}>
                Alle ansehen
              </a>
            </div>
            {activity.length === 0 ? (
              <p className="text-sm" style={{ color: "var(--text-muted)" }}>Noch keine Einträge.</p>
            ) : (
              <div className="space-y-2">
                {activity.map((entry) => (
                  <div key={entry.id} className="flex items-start gap-3 py-1.5 border-b last:border-0" style={{ borderColor: "var(--border)" }}>
                    <div className="w-1.5 h-1.5 rounded-full mt-1.5 shrink-0" style={{ backgroundColor: actionColor(entry.action) }} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <code className="text-xs font-mono" style={{ color: actionColor(entry.action) }}>{entry.action}</code>
                        <span className="text-[10px] font-mono" style={{ color: "var(--text-muted)" }}>
                          {new Date(entry.created_at).toLocaleString()}
                        </span>
                      </div>
                      {entry.detail && (
                        <p className="text-xs mt-0.5 truncate" style={{ color: "var(--text-secondary)" }}>{entry.detail}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>

        <div className="space-y-4">
          <div className="card" style={{ borderColor: "var(--accent-border)" }}>
            <h2 className="text-sm font-semibold mb-1" style={{ color: "var(--text-primary)" }}>Fleet Config</h2>
            <p className="text-xs mb-3" style={{ color: "var(--text-muted)" }}>
              Wird an alle Fleet-Server verteilt (Pull ~60s + Neustart). IDs/Keys bleiben je Server.
            </p>
            <label className="text-xs block mb-1" style={{ color: "var(--text-secondary)" }}>Dashboard URL</label>
            <input
              className="input font-mono text-xs w-full"
              value={fleetUrl}
              onChange={(e) => setFleetUrl(e.target.value)}
              placeholder="https://statusmod-dashboard.vercel.app"
              spellCheck={false}
              disabled={!loaded}
            />
            <label className="text-xs block mb-1 mt-3" style={{ color: "var(--text-secondary)" }}>Setup Secret</label>
            <input
              className="input font-mono text-xs w-full"
              type="password"
              value={fleetSecret}
              onChange={(e) => setFleetSecret(e.target.value)}
              placeholder="Gleicher Wert für alle Server"
              spellCheck={false}
              autoComplete="new-password"
              disabled={!loaded}
            />
            <button className="btn-primary w-full mt-4" onClick={handleSave} disabled={saving || !loaded}>
              {saving ? "Saving..." : "Verteilen"}
            </button>
            {message && (
              <p className={`text-xs mt-2 ${message.startsWith("Fleet config gespeichert") ? "text-emerald-400" : "text-red-400"}`}>
                {message}
              </p>
            )}
            <p className="text-[11px] font-mono mt-2" style={{ color: "var(--text-muted)" }}>
              Datei auf Servern: config/statusmodfleet/config.json
            </p>
          </div>

          <div className="card">
            <h2 className="text-sm font-semibold mb-2" style={{ color: "var(--text-primary)" }}>Aktionen</h2>
            <div className="grid grid-cols-1 gap-2">
              {[
                { href: "/dashboard/players", label: "Spieler verwalten", icon: "◎" },
                { href: "/dashboard/keys", label: "API-Keys", icon: "⌨" },
                { href: "/dashboard/config", label: "Server-Config", icon: "⚙" },
                { href: "/dashboard/audit", label: "Audit-Log", icon: "◉" },
              ].map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="flex items-center gap-2 px-3 py-2.5 rounded-lg transition-colors text-sm"
                  style={{ border: "1px solid var(--border)", color: "var(--text-secondary)" }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = "var(--bg-hover)"
                    e.currentTarget.style.color = "var(--text-primary)"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = ""
                    e.currentTarget.style.color = "var(--text-secondary)"
                  }}
                >
                  <span style={{ color: "var(--accent)" }}>{link.icon}</span>
                  {link.label}
                </a>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
