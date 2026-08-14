"use client"

import { useEffect, useState } from "react"
import { apiFetch, getServerId } from "@/lib/client/auth"

type HealthData = {
  status: string
  database: string
  timestamp: string
}

export default function DashboardOverview() {
  const [health, setHealth] = useState<HealthData | null>(null)
  const [playerCount, setPlayerCount] = useState(0)

  useEffect(() => {
    fetch("/api/health")
      .then((r) => r.json())
      .then((d) => setHealth(d))
      .catch(() => {})

    const serverId = getServerId()
    if (serverId) {
      apiFetch(`/api/players/${serverId}`)
        .then((r) => r.json())
        .then((d) => Array.isArray(d) && setPlayerCount(d.length))
        .catch(() => {})
    }
  }, [])

  const stats = [
    { label: "Server Status", value: health?.status ?? "—", badge: health?.status === "healthy" ? "badge-green" : "badge-yellow" },
    { label: "Database", value: health?.database ?? "—", badge: health?.database === "connected" ? "badge-green" : "badge-red" },
    { label: "Players", value: String(playerCount), badge: "" },
    { label: "API", value: "Online", badge: "badge-green" },
  ]

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display text-[#e8e6e0] mb-1">Dashboard</h1>
        <p className="text-sm text-[#636980]">Overview of your server</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        {stats.map((s) => (
          <div key={s.label} className="card">
            <div className="text-xs text-[#636980] font-medium mb-2">{s.label}</div>
            <div className="flex items-center gap-2">
              <span className="text-lg font-semibold text-[#e8e6e0]">{s.value}</span>
              {s.badge && <span className={s.badge}>{s.badge === "badge-green" ? "OK" : "?"}</span>}
            </div>
          </div>
        ))}
      </div>

      <div className="card">
        <h2 className="text-sm font-semibold text-[#e8e6e0] mb-2">Quick Links</h2>
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
              className="flex items-center gap-2 px-3 py-3 rounded-lg border border-[#2d3242] hover:bg-[#23273a] transition-colors text-sm text-[#9ea3b3] hover:text-[#e8e6e0]"
            >
              <span className="text-[#d4c892]">{link.icon}</span>
              {link.label}
            </a>
          ))}
        </div>
      </div>
    </div>
  )
}
