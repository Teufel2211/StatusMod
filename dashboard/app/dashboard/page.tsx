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

export default function DashboardOverview() {
  const [health, setHealth] = useState<HealthData | null>(null)
  const { players: livePlayers, connection } = useRealtimePlayers()

  useEffect(() => {
    fetch("/api/health")
      .then((r) => r.json())
      .then((d) => setHealth(d))
      .catch(() => {})
  }, [])

  const playerCount = livePlayers.length
  const connectionLabel = connection === "connected" ? "Live" : connection === "connecting" ? "Connecting..." : "Offline"
  const connectionBadge = connection === "connected" ? "badge-green" : connection === "connecting" ? "badge-yellow" : "badge-red"

  const stats = [
    { label: "Server Status", value: health?.status === "healthy" ? "Online" : (health?.status ?? "—"), badge: health?.status === "healthy" ? "badge-green" : "badge-yellow" },
    { label: "Database", value: health?.database === "connected" ? "Linked" : (health?.database ?? "—"), badge: health?.database === "connected" ? "badge-green" : "badge-red" },
    { label: "Players", value: String(playerCount), badge: "" },
    { label: "Realtime", value: connectionLabel, badge: connectionBadge },
  ]

  return (
    <div>
      <div className="mb-8 fade-up">
        <div className="kicker mb-2">Ops console</div>
        <h1 className="font-display font-bold text-3xl text-glow" style={{ color: "var(--text-primary)" }}>
          Dash<span className="text-gradient">board</span>
        </h1>
        <p className="text-sm mt-1" style={{ color: "var(--text-muted)" }}>Overview of your server</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {stats.map((s, i) => (
          <div key={s.label} className={`card card-glow fade-up anim-d${i + 1}`}>
            <div className="kicker mb-2" style={{ fontSize: "10px" }}>{s.label}</div>
            <div className="flex items-center gap-2">
              <span className="stat-num">{s.value}</span>
              {s.badge === "badge-green" && <span className="badge-green">OK</span>}
              {s.badge === "badge-red" && <span className="badge-red">!</span>}
              {s.badge === "badge-yellow" && <span className="badge-yellow">?</span>}
            </div>
          </div>
        ))}
      </div>

      {livePlayers.length > 0 && (
        <div className="card card-glow mb-8 fade-up anim-d3">
          <div className="flex items-center gap-2 mb-3">
            <span className="pulse-dot" />
            <h2 className="font-display font-semibold" style={{ color: "var(--text-primary)" }}>Live Players</h2>
          </div>
          <div className="flex flex-wrap gap-2">
            {livePlayers.map((p) => (
              <a
                key={p.uuid}
                href={`/dashboard/players/${p.uuid}`}
                className="flex items-center gap-2 px-3 py-2 rounded-lg transition-all text-sm hover:-translate-y-0.5"
                style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-primary)" }}
                onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--accent-border)"; e.currentTarget.style.boxShadow = "0 0 14px var(--glow-accent)" }}
                onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.boxShadow = "" }}
              >
                <PlayerAvatar uuid={p.uuid} username={p.username} avatar={p.avatar} sizePx={20} />
                <span style={{ color: "var(--text-primary)" }}>{p.username ?? "Unknown"}</span>
                {p.status && (
                  <span className="text-xs" style={{ color: "var(--text-muted)" }}>{p.status}</span>
                )}
              </a>
            ))}
          </div>
        </div>
      )}

      <div className="card fade-up anim-d4">
        <div className="kicker mb-3">Shortcuts</div>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {[
            { href: "/dashboard/players", label: "Manage Players", icon: "◎" },
            { href: "/dashboard/config", label: "Server Config", icon: "⚙" },
            { href: "/dashboard/keys", label: "API Keys", icon: "⌨" },
            { href: "/dashboard/audit", label: "Audit Log", icon: "◉" },
            { href: "/dashboard/fleet", label: "Fleet Admin", icon: "⬡" },
          ].map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="flex items-center gap-2 px-3 py-3 rounded-lg transition-all text-sm hover:-translate-y-0.5"
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
              <span className="font-display" style={{ color: "var(--accent)" }}>{link.icon}</span>
              {link.label}
            </a>
          ))}
        </div>
      </div>
    </div>
  )
}
