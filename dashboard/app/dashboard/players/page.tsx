"use client"

import { useEffect, useState } from "react"
import { apiFetch, getServerId } from "@/lib/client/auth"
import { useRealtimePlayers } from "@/lib/client/use-realtime"
import { cssColor } from "@/lib/color"
import { PlayerAvatar } from "@/components/player-avatar"

type PlayerRow = {
  id: number
  server_id: string
  uuid: string
  username: string | null
  status: string | null
  color: string | null
  avatar: string | null
  is_online: boolean | null
  created_at: string
}

export default function PlayersPage() {
  const [players, setPlayers] = useState<PlayerRow[]>([])
  const [loading, setLoading] = useState(true)
  const { players: livePlayers, connection } = useRealtimePlayers()

  useEffect(() => {
    const serverId = getServerId()
    if (!serverId) { setLoading(false); return }

    apiFetch(`/api/players/${serverId}`)
      .then((r) => r.json())
      .then((d) => setPlayers(Array.isArray(d) ? d : []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  const liveMap = new Map(livePlayers.map((p) => [p.uuid, p]))

  const merged = players.map((p) => {
    const live = liveMap.get(p.uuid)
    if (!live) return p
    return {
      ...p,
      username: live.username ?? p.username,
      status: live.status ?? p.status,
      color: live.color ?? p.color,
      avatar: live.avatar ?? p.avatar,
    }
  })

  const onlineCount = merged.filter((p) => p.is_online).length

  return (
    <div>
      <div className="mb-8 fade-up">
        <div className="kicker mb-2">Roster · {players.length} known · {onlineCount} online</div>
        <div className="flex items-center gap-3 flex-wrap">
          <h1 className="font-display font-bold text-3xl text-glow" style={{ color: "var(--text-primary)" }}>Players</h1>
          {connection === "connected" && (
            <span className="badge-green"><span className="pulse-dot mr-1.5" />live</span>
          )}
        </div>
      </div>

      {loading ? (
        <div className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>$ scanning roster…</div>
      ) : players.length === 0 ? (
        <div className="card card-glow text-center py-12 fade-up">
          <div className="font-display text-4xl mb-3" style={{ color: "var(--accent)", opacity: 0.5 }}>◎</div>
          <p className="text-sm" style={{ color: "var(--text-muted)" }}>No players yet</p>
          <p className="text-xs mt-1 font-mono" style={{ color: "var(--text-muted)" }}>Players will appear here when they join the server</p>
        </div>
      ) : (
        <div className="card card-glow p-0 overflow-hidden fade-up anim-d1">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b font-mono text-[11px] uppercase tracking-widest" style={{ borderColor: "var(--border)", backgroundColor: "var(--bg-primary)", color: "var(--text-muted)" }}>
                <th className="text-left px-5 py-3 font-medium">Username</th>
                <th className="text-left px-5 py-3 font-medium">Status</th>
                <th className="text-left px-5 py-3 font-medium">UUID</th>
                <th className="text-left px-5 py-3 font-medium">Joined</th>
              </tr>
            </thead>
            <tbody>
              {merged.map((p) => (
                <tr
                  key={p.id}
                  className="border-b last:border-0 transition-all cursor-pointer hover:-translate-y-px"
                  style={{ borderColor: "var(--border)", opacity: p.is_online ? 1 : 0.5 }}
                  onClick={() => window.location.href = `/dashboard/players/${p.uuid}`}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = "var(--bg-hover)"
                    e.currentTarget.style.opacity = "1"
                    e.currentTarget.style.boxShadow = "inset 2px 0 0 var(--accent)"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = ""
                    e.currentTarget.style.opacity = p.is_online ? "1" : "0.5"
                    e.currentTarget.style.boxShadow = ""
                  }}
                >
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-2.5">
                      <PlayerAvatar uuid={p.uuid} username={p.username} avatar={p.avatar} sizePx={24} />
                      <span
                        className="w-1.5 h-1.5 rounded-full inline-block"
                        style={p.is_online
                          ? { backgroundColor: "#10b981", boxShadow: "0 0 8px rgba(16,185,129,0.8)" }
                          : { backgroundColor: "var(--border)" }}
                        title={p.is_online ? "online" : "offline"}
                      />
                      <span className="font-medium" style={{ color: "var(--text-primary)" }}>{p.username ?? "—"}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3.5">
                    {p.status ? (
                      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full" style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-primary)" }}>
                        {p.color && (
                          <span
                            className="w-2 h-2 rounded-full inline-block"
                            style={{ background: cssColor(p.color), boxShadow: `0 0 8px ${cssColor(p.color)}` }}
                          />
                        )}
                        <span style={{ color: "var(--text-secondary)" }}>{p.status}</span>
                      </span>
                    ) : (
                      <span style={{ color: "var(--text-muted)" }}>—</span>
                    )}
                  </td>
                  <td className="px-5 py-3.5">
                    <code className="text-xs font-mono" style={{ color: "var(--text-muted)" }}>{p.uuid.slice(0, 8)}...</code>
                  </td>
                  <td className="px-5 py-3.5 text-xs font-mono" style={{ color: "var(--text-muted)" }}>
                    {new Date(p.created_at).toLocaleDateString()}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
