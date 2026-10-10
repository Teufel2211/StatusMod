"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { apiFetch, getServerId, ensureSession } from "@/lib/client/auth"
import { useRealtimePlayers } from "@/lib/client/use-realtime"
import { PlayerAvatar } from "@/components/player-avatar"
import FleetTopbar from "@/components/fleet-topbar"
import Footer from "@/components/footer"

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
  if (!iso) return "noch nie"
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return `vor ${s} Sek.`
  const m = Math.floor(s / 60)
  if (m < 60) return `vor ${m} Min.`
  const h = Math.floor(m / 60)
  if (h < 48) return `vor ${h} Std.`
  return `vor ${Math.floor(h / 24)} Tg.`
}

function actionColor(action: string): string {
  const a = action.toLowerCase()
  if (a.includes("mute") || a.includes("block") || a.includes("ban")) return "#f87171"
  if (a.includes("fleet") || a.includes("config") || a.includes("key")) return "var(--accent)"
  if (a.includes("login") || a.includes("auth") || a.includes("code")) return "var(--cyan)"
  return "var(--text-secondary)"
}

export default function FleetAdminPage() {
  const router = useRouter()
  const [authed, setAuthed] = useState(false)
  const [checking, setChecking] = useState(true)
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
    let cancelled = false
    ensureSession().then((ok) => {
      if (cancelled) return
      if (ok) {
        setAuthed(true)
        setChecking(false)
      } else {
        router.push("/login")
      }
    })
    return () => {
      cancelled = true
    }
  }, [router])

  useEffect(() => {
    if (!authed) return
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
        .then((d) => setActivity(Array.isArray(d) ? d.slice(0, 10) : []))
        .catch(() => {})
    }
    apiFetch(`/api/fleet/config`)
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!d) {
          setMessage("Kein Zugriff – nur für Owner.")
          return
        }
        if (typeof d.dashboard_url === "string") setFleetUrl(d.dashboard_url)
        if (typeof d.setup_secret === "string") setFleetSecret(d.setup_secret)
        if (typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
        setLoaded(true)
      })
      .catch(() => setMessage("Laden fehlgeschlagen."))
  }, [authed])

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
        setMessage("Gespeichert. Die Server übernehmen es in ca. 1 Minute (Neustart erforderlich).")
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
  const live = connection === "connected"

  if (checking) {
    return (
      <div className="fleet-theme min-h-screen flex items-center justify-center" style={{ backgroundColor: "var(--bg-primary)" }}>
        <div className="flex items-center gap-3">
          <div className="w-2 h-2 rounded-full animate-pulse" style={{ backgroundColor: "var(--accent)" }} />
          <span className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>Verbindung wird aufgebaut…</span>
        </div>
      </div>
    )
  }

  if (!authed) return null

  return (
    <div className="fleet-theme min-h-screen hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)", color: "var(--text-primary)" }}>
      <FleetTopbar />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
      <div className="card card-glow mb-6 fade-up overflow-hidden">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div>
            <div className="kicker mb-2">Zentrale Steuerung · alle Server</div>
            <h1 className="font-display font-bold text-3xl sm:text-4xl text-glow" style={{ color: "var(--text-primary)" }}>
              Fleet <span className="text-gradient">Admin</span>
            </h1>
            <p className="text-sm mt-2" style={{ color: "var(--text-muted)" }}>
              Ein Wert hier, alle Server dort {fleetUpdatedAt && <span>· geändert {ageString(fleetUpdatedAt)}</span>}
              {serverId !== "" && <span className="font-mono"> · {serverId.slice(0, 8)}…</span>}
            </p>
          </div>
          <div className="flex items-center gap-2 px-4 py-2.5 rounded-xl font-mono text-xs" style={{ border: "1px solid var(--accent-border)", backgroundColor: "var(--accent-dim)", color: "var(--accent)" }}>
            <span className="pulse-dot" />
            {fleetActive ? "FLEET BEREIT" : "FLEET LEER"}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {[
          { label: "Server", value: health?.status === "healthy" ? "Online" : "…", ok: health?.status === "healthy" ? true : null as boolean | null },
          { label: "Spieler online", value: String(livePlayers.length), ok: (livePlayers.length > 0 ? true : null) as boolean | null, sub: knownCount !== null ? `${knownCount} bekannt` : undefined },
          { label: "Live-Feed", value: live ? "Verbunden" : connection === "connecting" ? "…" : "Getrennt", ok: live ? true : connection === "connecting" ? null : false },
          { label: "Datenbank", value: health?.database === "connected" ? "Ok" : "…", ok: health?.database === "connected" ? true : null },
        ].map((s, i) => (
          <div key={s.label} className={`card fade-up anim-d${i + 1} !p-5`}>
            <div className="kicker mb-2" style={{ fontSize: "10px" }}>{s.label}</div>
            <div className="flex items-center gap-2">
              <span className="stat-num" style={{ fontSize: "1.5rem" }}>{s.value}</span>
              {s.ok === true && <span className="badge-green">OK</span>}
              {s.ok === false && <span className="badge-red">!</span>}
            </div>
            {s.sub && <div className="text-[11px] font-mono mt-1" style={{ color: "var(--text-muted)" }}>{s.sub}</div>}
          </div>
        ))}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-4 mb-6">
        <div className="lg:col-span-3 space-y-4">
          <div className="card card-glow fade-up anim-d2">
            <div className="flex items-center gap-2 mb-3">
              <span className="pulse-dot" />
              <h2 className="font-display font-semibold" style={{ color: "var(--text-primary)" }}>Spieler online ({livePlayers.length})</h2>
            </div>
            {livePlayers.length === 0 ? (
              <p className="text-sm" style={{ color: "var(--text-muted)" }}>Niemand online.</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {livePlayers.map((p) => (
                  <a
                    key={p.uuid}
                    href={`/dashboard/players/${p.uuid}`}
                    className="flex items-center gap-2 px-3 py-2 rounded-xl transition-all text-sm hover:-translate-y-0.5"
                    style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-primary)" }}
                    onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--accent-border)"; e.currentTarget.style.boxShadow = "0 0 14px var(--glow-accent)" }}
                    onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.boxShadow = "" }}
                  >
                    <PlayerAvatar uuid={p.uuid} username={p.username} avatar={p.avatar} sizePx={20} />
                    <span className="font-medium" style={{ color: "var(--text-primary)" }}>{p.username ?? "Unknown"}</span>
                    {p.status && (
                      <span className="text-xs px-2 py-0.5 rounded-full" style={{ backgroundColor: "var(--accent-dim)", color: "var(--accent)" }}>{p.status}</span>
                    )}
                  </a>
                ))}
              </div>
            )}
          </div>

          <div className="card fade-up anim-d3">
            <div className="flex items-center justify-between mb-3">
              <h2 className="font-display font-semibold" style={{ color: "var(--text-primary)" }}>Letzte Aktivität</h2>
              <a href="/dashboard/audit" className="font-mono text-xs underline underline-offset-2" style={{ color: "var(--text-muted)" }}>
                Alle ansehen →
              </a>
            </div>
            {activity.length === 0 ? (
              <p className="text-sm" style={{ color: "var(--text-muted)" }}>Noch keine Einträge.</p>
            ) : (
              <div className="space-y-2">
                {activity.map((entry) => (
                  <div key={entry.id} className="flex items-start gap-3 py-1.5 border-b last:border-0" style={{ borderColor: "var(--border)" }}>
                    <div className="w-1.5 h-1.5 rounded-full mt-1.5 shrink-0" style={{ backgroundColor: actionColor(entry.action), boxShadow: `0 0 8px ${actionColor(entry.action)}` }} />
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <code className="text-xs font-mono font-semibold" style={{ color: actionColor(entry.action) }}>{entry.action}</code>
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

        <div className="lg:col-span-2 space-y-4">
          <div className="card card-glow fade-up anim-d2">
            <div className="kicker mb-2">Verteilen</div>
            <h2 className="font-display font-semibold mb-1" style={{ color: "var(--text-primary)" }}>Fleet-Werte</h2>
            <p className="text-xs mb-4" style={{ color: "var(--text-muted)" }}>
              An alle Fleet-Server. IDs und Keys bleiben je Server erhalten.
            </p>
            <label className="label">Dashboard-URL</label>
            <input
              className="input font-mono text-xs w-full"
              value={fleetUrl}
              onChange={(e) => setFleetUrl(e.target.value)}
              placeholder="https://statusmod-dashboard.vercel.app"
              spellCheck={false}
              disabled={!loaded}
            />
            <label className="label mt-4">Setup-Secret</label>
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
            <button className="btn-primary w-full mt-4 text-sm" onClick={handleSave} disabled={saving || !loaded}>
              {saving ? "Wird verteilt…" : "Auf alle Server verteilen"}
            </button>
            {message && (
              <p className={`text-xs mt-3 font-mono ${message.startsWith("Gespeichert") ? "text-emerald-400" : "text-red-400"}`}>
                {message}
              </p>
            )}
            <p className="text-[11px] font-mono mt-3" style={{ color: "var(--text-muted)" }}>
              Datei: config/statusmodfleet/config.json · ca. 1 Minute + Neustart
            </p>
          </div>

          <div className="card fade-up anim-d4">
            <div className="kicker mb-3">Bereiche</div>
            <div className="grid grid-cols-1 gap-2">
              {[
                { href: "/dashboard/players", label: "Spieler", desc: "Übersicht + Status", icon: "◎" },
                { href: "/dashboard/keys", label: "Keys", desc: "API-Zugang", icon: "⌨" },
                { href: "/dashboard/config", label: "Config", desc: "Server-JSON", icon: "⚙" },
                { href: "/dashboard/audit", label: "Protokoll", desc: "Alle Aktionen", icon: "◉" },
              ].map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="flex items-center gap-3 px-3 py-2.5 rounded-xl transition-all text-sm hover:-translate-y-0.5"
                  style={{ border: "1px solid var(--border)", color: "var(--text-secondary)", backgroundColor: "var(--bg-primary)" }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = "var(--accent-border)"
                    e.currentTarget.style.color = "var(--text-primary)"
                    e.currentTarget.style.boxShadow = "0 0 14px var(--glow-accent)"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = "var(--border)"
                    e.currentTarget.style.color = "var(--text-secondary)"
                    e.currentTarget.style.boxShadow = ""
                  }}
                >
                  <span style={{ color: "var(--accent)" }}>{link.icon}</span>
                  <span className="font-medium">{link.label}</span>
                  <span className="text-xs" style={{ color: "var(--text-muted)" }}>— {link.desc}</span>
                </a>
              ))}
            </div>
          </div>
        </div>
      </div>

      <p className="font-mono text-[11px] fade-up anim-d5" style={{ color: "var(--text-muted)" }}>
        <span style={{ color: "var(--accent)" }}>Tipp:</span> Owner-Code mit <span style={{ color: "var(--text-secondary)" }}>/fleet owner-code</span> in der Server-Konsole erzeugen, dann hier über Login einlösen.
      </p>
      </div>
      <Footer className="px-6 pb-6" />
    </div>
  )
}
