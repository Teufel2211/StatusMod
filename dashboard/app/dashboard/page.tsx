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
  const [knownCount, setKnownCount] = useState<number | null>(null)
  const { players: livePlayers, connection } = useRealtimePlayers()

  useEffect(() => {
    fetch("/api/health")
      .then((r) => r.json())
      .then((d) => setHealth(d))
      .catch(() => {})
    const sid = getServerId()
    if (sid) {
      apiFetch(`/api/players/${sid}`)
        .then((r) => (r.ok ? r.json() : []))
        .then((d) => setKnownCount(Array.isArray(d) ? d.length : null))
        .catch(() => {})
    }
  }, [])

  const live = connection === "connected"

  return (
    <div>
      <div className="card card-glow mb-6 fade-up overflow-hidden">
        <div className="flex items-center justify-between gap-4 flex-wrap">
          <div>
            <div className="kicker mb-2">Live ops · {live ? "streaming" : connection}</div>
            <h1 className="font-display font-bold text-3xl sm:text-4xl text-glow" style={{ color: "var(--text-primary)" }}>
              {livePlayers.length > 0 ? (
                <><span className="text-gradient">{livePlayers.length}</span> online now</>
              ) : (
                <>All quiet<span className="text-gradient">.</span></>
              )}
            </h1>
            <p className="text-sm mt-2" style={{ color: "var(--text-muted)" }}>
              {knownCount !== null ? `${knownCount} known players` : "Loading roster…"}
              {" · "}{health?.status === "healthy" ? "systems nominal" : "checking systems…"}
            </p>
          </div>
          <div className="flex items-center gap-2 px-4 py-2 rounded-xl font-mono text-xs" style={{ border: "1px solid var(--accent-border)", backgroundColor: "var(--accent-dim)", color: "var(--accent)" }}>
            <span className="pulse-dot" />
            {live ? "LIVE FEED" : connection.toUpperCase()}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {[
          { label: "Server", value: health?.status === "healthy" ? "Online" : (health?.status ?? "—"), ok: health?.status === "healthy" },
          { label: "Database", value: health?.database === "connected" ? "Linked" : (health?.database ?? "—"), ok: health?.database === "connected" },
          { label: "Known players", value: knownCount !== null ? String(knownCount) : "—", ok: null },
          { label: "Realtime", value: live ? "Live" : connection === "connecting" ? "…" : "Off", ok: live ? true : connection === "connecting" ? null : false },
        ].map((s, i) => (
          <div key={s.label} className={`card fade-up anim-d${i + 1} !p-5`}>
            <div className="kicker mb-2" style={{ fontSize: "10px" }}>{s.label}</div>
            <div className="flex items-center gap-2">
              <span className="stat-num" style={{ fontSize: "1.5rem" }}>{s.value}</span>
              {s.ok === true && <span className="badge-green">OK</span>}
              {s.ok === false && <span className="badge-red">!</span>}
              {s.ok === null && s.label === "Realtime" && <span className="badge-yellow">?</span>}
            </div>
          </div>
        ))}
      </div>

      <div className="card card-glow mb-6 fade-up anim-d3">
        <div className="flex items-center justify-between mb-3 flex-wrap gap-2">
          <div className="flex items-center gap-2">
            <span className="pulse-dot" />
            <h2 className="font-display font-semibold" style={{ color: "var(--text-primary)" }}>Live Players</h2>
          </div>
          <a href="/dashboard/players" className="font-mono text-xs underline underline-offset-2" style={{ color: "var(--text-muted)" }}>
            roster →
          </a>
        </div>
        {livePlayers.length === 0 ? (
          <p className="text-sm" style={{ color: "var(--text-muted)" }}>Nobody online right now — the feed lights up on join.</p>
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

      <div className="card fade-up anim-d4">
        <div className="kicker mb-3">Shortcuts</div>
        <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-5 gap-3">
          {[
            { href: "/dashboard/players", label: "Players", icon: "◎", hint: "roster" },
            { href: "/dashboard/fleet", label: "Fleet", icon: "⬡", hint: "distribute" },
            { href: "/dashboard/config", label: "Config", icon: "⚙", hint: "json" },
            { href: "/dashboard/keys", label: "Keys", icon: "⌨", hint: "access" },
            { href: "/dashboard/audit", label: "Audit", icon: "◉", hint: "trail" },
          ].map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="rounded-xl px-3 py-3.5 transition-all hover:-translate-y-0.5 group"
              style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-primary)" }}
              onMouseEnter={(e) => {
                e.currentTarget.style.borderColor = "var(--accent-border)"
                e.currentTarget.style.boxShadow = "0 0 18px var(--glow-accent)"
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.borderColor = "var(--border)"
                e.currentTarget.style.boxShadow = ""
              }}
            >
              <div className="font-display text-xl mb-1" style={{ color: "var(--accent)" }}>{link.icon}</div>
              <div className="text-sm font-medium" style={{ color: "var(--text-primary)" }}>{link.label}</div>
              <div className="font-mono text-[11px]" style={{ color: "var(--text-muted)" }}>{link.hint}</div>
            </a>
          ))}
        </div>
      </div>
    </div>
  )
}
