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
    { label: "Server Status", value: health?.status ?? "—", badge: health?.status === "healthy" ? "badge-green" : "badge-yellow" },
    { label: "Database", value: health?.database ?? "—", badge: health?.database === "connected" ? "badge-green" : "badge-red" },
    { label: "Players", value: String(playerCount), badge: "" },
    { label: "Realtime", value: connectionLabel, badge: connectionBadge },
  ]

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display mb-1" style={{ color: "var(--text-primary)" }}>Dashboard</h1>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>Overview of your server</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {stats.map((s) => (
          <div key={s.label} className="card">
            <div className="text-xs font-medium mb-2" style={{ color: "var(--text-muted)" }}>{s.label}</div>
            <div className="flex items-center gap-2">
              <span className="text-lg font-semibold" style={{ color: "var(--text-primary)" }}>{s.value}</span>
              {s.badge && <span className={s.badge}>{s.badge === "badge-green" ? "OK" : s.badge === "badge-red" ? "!" : "?"}</span>}
            </div>
          </div>
        ))}
      </div>

      {livePlayers.length > 0 && (
        <div className="card mb-8">
          <h2 className="text-sm font-semibold mb-3" style={{ color: "var(--text-primary)" }}>Live Players</h2>
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
        </div>
      )}

      <div className="card">
        <h2 className="text-sm font-semibold mb-2" style={{ color: "var(--text-primary)" }}>Quick Links</h2>
        <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
          {[
            { href: "/dashboard/players", label: "Manage Players", icon: "◎" },
            { href: "/dashboard/config", label: "Server Config", icon: "⚙" },
            { href: "/dashboard/keys", label: "API Keys", icon: "⌨" },
            { href: "/dashboard/audit", label: "Audit Log", icon: "◉" },
          ].map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="flex items-center gap-2 px-3 py-3 rounded-lg transition-colors text-sm"
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
  )
}
