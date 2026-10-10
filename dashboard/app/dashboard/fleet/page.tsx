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
  if (!iso) return "--"
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return `${s}s ago`
  const m = Math.floor(s / 60)
  if (m < 60) return `${m}m ago`
  const h = Math.floor(m / 60)
  if (h < 48) return `${h}h ago`
  return `${Math.floor(h / 24)}d ago`
}

function actionColor(action: string): string {
  const a = action.toLowerCase()
  if (a.includes("mute") || a.includes("block") || a.includes("ban")) return "#ff5f5f"
  if (a.includes("fleet") || a.includes("config") || a.includes("key")) return "var(--accent)"
  if (a.includes("login") || a.includes("auth") || a.includes("code")) return "var(--cyan)"
  return "var(--text-secondary)"
}

const ASCII_HR = "─".repeat(72)

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
        .then((d) => setActivity(Array.isArray(d) ? d.slice(0, 10) : []))
        .catch(() => {})
    }
    apiFetch(`/api/fleet/config`)
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!d) {
          setMessage("ACCESS DENIED — owner required.")
          return
        }
        if (typeof d.dashboard_url === "string") setFleetUrl(d.dashboard_url)
        if (typeof d.setup_secret === "string") setFleetSecret(d.setup_secret)
        if (typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
        setLoaded(true)
      })
      .catch(() => setMessage("UPLINK FAILED."))
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
        setMessage(">> DISTRIBUTED. Servers pull within ~60s + restart.")
        apiFetch(`/api/fleet/config`)
          .then((r) => (r.ok ? r.json() : null))
          .then((d) => {
            if (d && typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
          })
          .catch(() => {})
      } else setMessage(">> WRITE FAILED.")
    } catch {
      setMessage(">> WRITE FAILED.")
    } finally {
      setSaving(false)
    }
  }

  const fleetActive = fleetSecret !== "" || fleetUrl !== ""
  const sysRows: Array<[string, string, string]> = [
    ["fleet.link", fleetActive ? "ACTIVE" : "EMPTY", fleetActive ? "var(--accent)" : "var(--cyan)"],
    ["fleet.updated", ageString(fleetUpdatedAt), "var(--text-secondary)"],
    ["sys.api", health?.status === "healthy" ? "ONLINE" : (health?.status ?? "--"), health?.status === "healthy" ? "var(--accent)" : "var(--cyan)"],
    ["sys.db", health?.database === "connected" ? "LINKED" : (health?.database ?? "--"), health?.database === "connected" ? "var(--accent)" : "#ff5f5f"],
    ["net.players", `${livePlayers.length} live / ${knownCount ?? "?"} known`, "var(--text-primary)"],
    ["net.feed", connection.toUpperCase(), connection === "connected" ? "var(--accent)" : "var(--cyan)"],
    ["srv.id", serverId ? `${serverId.slice(0, 8)}…` : "--", "var(--text-muted)"],
  ]

  return (
    <div className="font-mono">
      <div className="mb-6 fade-up">
        <div className="text-xs tracking-widest mb-2" style={{ color: "var(--text-muted)" }}>
          {"//"} privileged zone — fleet distribution control
        </div>
        <h1 className="font-bold text-3xl sm:text-4xl fleet-phosphor">
          FLEET<span className="fleet-blink">_</span>COMMAND
        </h1>
        <div className="fleet-ascii-hr mt-3 hidden sm:block" aria-hidden>{ASCII_HR}</div>
      </div>

      <div className="card p-0 overflow-hidden mb-6 fade-up anim-d1">
        <div className="term-bar">
          <span className="term-dot" style={{ backgroundColor: "#ff5f5f" }} />
          <span className="term-dot" style={{ backgroundColor: "var(--cyan)" }} />
          <span className="term-dot" style={{ backgroundColor: "var(--accent)" }} />
          <span className="ml-2">$ fleet --status --watch</span>
        </div>
        <div className="p-0">
          {sysRows.map(([k, v, c]) => (
            <div
              key={k}
              className="flex items-center justify-between gap-4 px-4 sm:px-5 py-2.5 text-xs sm:text-sm border-b last:border-0"
              style={{ borderColor: "var(--border)" }}
            >
              <span style={{ color: "var(--text-muted)" }}>{k}</span>
              <span className="font-bold text-right" style={{ color: c }}>{v}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-6 mb-6">
        <div className="lg:col-span-3 space-y-6">
          <div className="card p-0 overflow-hidden fade-up anim-d2">
            <div className="term-bar">
              <span className="pulse-dot" />
              <span className="ml-2">live roster [{livePlayers.length}]</span>
            </div>
            <div className="p-4">
              {livePlayers.length === 0 ? (
                <p className="text-sm" style={{ color: "var(--text-muted)" }}>-- no signals. nobody online.</p>
              ) : (
                <div className="flex flex-wrap gap-2">
                  {livePlayers.map((p) => (
                    <a
                      key={p.uuid}
                      href={`/dashboard/players/${p.uuid}`}
                      className="flex items-center gap-2 px-3 py-2 rounded transition-all text-sm hover:-translate-y-0.5"
                      style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-primary)" }}
                      onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--accent-border)"; e.currentTarget.style.boxShadow = "0 0 14px var(--glow-accent)" }}
                      onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.boxShadow = "" }}
                    >
                      <PlayerAvatar uuid={p.uuid} username={p.username} avatar={p.avatar} sizePx={20} />
                      <span style={{ color: "var(--text-primary)" }}>{p.username ?? "Unknown"}</span>
                      {p.status && (
                        <span className="text-xs" style={{ color: "var(--text-muted)" }}>[{p.status}]</span>
                      )}
                    </a>
                  ))}
                </div>
              )}
            </div>
          </div>

          <div className="card p-0 overflow-hidden fade-up anim-d3">
            <div className="term-bar">
              <span className="ml-0">$ tail -f audit.log</span>
              <span className="flex-1" />
              <a href="/dashboard/audit" className="underline underline-offset-2" style={{ color: "var(--text-muted)" }}>
                full log →
              </a>
            </div>
            <div className="p-4">
              {activity.length === 0 ? (
                <p className="text-sm" style={{ color: "var(--text-muted)" }}>-- buffer empty.</p>
              ) : (
                <div className="space-y-1.5">
                  {activity.map((entry) => (
                    <div key={entry.id} className="flex items-baseline gap-3 text-xs sm:text-sm">
                      <span className="shrink-0 tabular-nums" style={{ color: "var(--text-muted)" }}>
                        {new Date(entry.created_at).toLocaleTimeString()}
                      </span>
                      <span className="shrink-0 font-bold" style={{ color: actionColor(entry.action) }}>
                        [{entry.action}]
                      </span>
                      {entry.detail && (
                        <span className="truncate" style={{ color: "var(--text-secondary)" }}>{entry.detail}</span>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>

        <div className="lg:col-span-2 space-y-6">
          <div className="card p-0 overflow-hidden card-glow fade-up anim-d2">
            <div className="term-bar">
              <span className="term-dot" style={{ backgroundColor: "#ff5f5f" }} />
              <span className="term-dot" style={{ backgroundColor: "var(--cyan)" }} />
              <span className="term-dot" style={{ backgroundColor: "var(--accent)" }} />
              <span className="ml-2">$ fleet push --all</span>
            </div>
            <div className="p-4 sm:p-5">
              <p className="text-xs mb-4" style={{ color: "var(--text-muted)" }}>
                {"//"} Verteilt URL + Secret an alle Fleet-Server. IDs/Keys bleiben je Server.
              </p>
              <label className="text-xs block mb-1 tracking-widest" style={{ color: "var(--text-secondary)" }}>DASHBOARD_URL</label>
              <input
                className="input text-xs w-full"
                value={fleetUrl}
                onChange={(e) => setFleetUrl(e.target.value)}
                placeholder="https://statusmod-dashboard.vercel.app"
                spellCheck={false}
                disabled={!loaded}
              />
              <label className="text-xs block mb-1 mt-4 tracking-widest" style={{ color: "var(--text-secondary)" }}>SETUP_SECRET</label>
              <input
                className="input text-xs w-full"
                type="password"
                value={fleetSecret}
                onChange={(e) => setFleetSecret(e.target.value)}
                placeholder="••••••••••••••••"
                spellCheck={false}
                autoComplete="new-password"
                disabled={!loaded}
              />
              <button className="btn-primary w-full mt-4 font-mono text-sm" onClick={handleSave} disabled={saving || !loaded}>
                {saving ? "[...] pushing..." : "[>] DISTRIBUTE TO ALL SERVERS"}
              </button>
              {message && (
                <p className="text-xs mt-3 font-mono" style={{ color: message.startsWith(">> DISTRIBUTED") ? "var(--accent)" : "#ff5f5f" }}>
                  {message}
                </p>
              )}
              <p className="text-[11px] font-mono mt-3" style={{ color: "var(--text-muted)" }}>
                target: config/statusmodfleet/config.json · pull ~60s + restart
              </p>
            </div>
          </div>

          <div className="card p-0 overflow-hidden fade-up anim-d4">
            <div className="term-bar">
              <span className="ml-0">$ ls actions/</span>
            </div>
            <div className="p-4 grid grid-cols-1 gap-2">
              {[
                { href: "/dashboard/players", label: "players/", desc: "roster + status" },
                { href: "/dashboard/keys", label: "keys/", desc: "api access" },
                { href: "/dashboard/config", label: "config/", desc: "server json" },
                { href: "/dashboard/audit", label: "audit/", desc: "full log" },
              ].map((link) => (
                <a
                  key={link.href}
                  href={link.href}
                  className="flex items-center gap-3 px-3 py-2.5 rounded transition-all text-sm font-mono"
                  style={{ border: "1px solid var(--border)", color: "var(--text-secondary)", backgroundColor: "var(--bg-primary)" }}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.borderColor = "var(--accent-border)"
                    e.currentTarget.style.color = "var(--accent)"
                    e.currentTarget.style.boxShadow = "0 0 14px var(--glow-accent)"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.borderColor = "var(--border)"
                    e.currentTarget.style.color = "var(--text-secondary)"
                    e.currentTarget.style.boxShadow = ""
                  }}
                >
                  <span style={{ color: "var(--accent)" }}>▸</span>
                  {link.label}
                  <span className="text-xs" style={{ color: "var(--text-muted)" }}>— {link.desc}</span>
                </a>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div className="fleet-ascii-hr mb-2 hidden sm:block" aria-hidden>{ASCII_HR}</div>
      <p className="font-mono text-[11px]" style={{ color: "var(--text-muted)" }}>
        {"//"} owner-code: <span style={{ color: "var(--text-secondary)" }}>/fleet owner-code</span> in der Server-Konsole → hier über Login einlösen<span className="fleet-blink" style={{ color: "var(--accent)" }}>▌</span>
      </p>
    </div>
  )
}
