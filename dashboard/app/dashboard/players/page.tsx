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

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display mb-1" style={{ color: "var(--text-primary)" }}>Players</h1>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>
          {players.length} known player(s) · {merged.filter((p) => p.is_online).length} online
          {connection === "connected" && (
            <span className="ml-2 text-xs badge-green">live</span>
          )}
        </p>
      </div>

      {loading ? (
        <div className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>Loading...</div>
      ) : players.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-3xl mb-3 opacity-30">◎</div>
          <p className="text-sm" style={{ color: "var(--text-muted)" }}>No players yet</p>
          <p className="text-xs mt-1" style={{ color: "var(--text-muted)" }}>Players will appear here when they join the server</p>
        </div>
      ) : (
        <div className="card p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b" style={{ borderColor: "var(--border)" }}>
                <th className="text-left px-5 py-3 font-medium text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Username</th>
                <th className="text-left px-5 py-3 font-medium text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Status</th>
                <th className="text-left px-5 py-3 font-medium text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>UUID</th>
                <th className="text-left px-5 py-3 font-medium text-xs uppercase tracking-wider" style={{ color: "var(--text-muted)" }}>Joined</th>
              </tr>
            </thead>
            <tbody>
              {merged.map((p) => (
                <tr
                  key={p.id}
                  className="border-b last:border-0 transition-colors cursor-pointer"
                  style={{ borderColor: "var(--border)", opacity: p.is_online ? 1 : 0.5 }}
                  onClick={() => window.location.href = `/dashboard/players/${p.uuid}`}
                  onMouseEnter={(e) => {
                    e.currentTarget.style.backgroundColor = "var(--bg-hover)"
                    e.currentTarget.style.opacity = "1"
                  }}
                  onMouseLeave={(e) => {
                    e.currentTarget.style.backgroundColor = ""
                    e.currentTarget.style.opacity = p.is_online ? "1" : "0.5"
                  }}
                >
                  <td className="px-5 py-3.5">
                    <div className="flex items-center gap-2.5">
                      <PlayerAvatar uuid={p.uuid} username={p.username} avatar={p.avatar} sizePx={24} />
                      <span
                        className={`w-1.5 h-1.5 rounded-full inline-block ${p.is_online ? "badge-green" : ""}`}
                        style={p.is_online ? undefined : { backgroundColor: "var(--border)" }}
                        title={p.is_online ? "online" : "offline"}
                      />
                      <span className="font-medium" style={{ color: "var(--text-primary)" }}>{p.username ?? "—"}</span>
                    </div>
                  </td>
                  <td className="px-5 py-3.5">
                    {p.status ? (
                      <span className="inline-flex items-center gap-1.5">
                        {p.color && (
                          <span
                            className="w-2 h-2 rounded-full inline-block"
                            style={{ background: cssColor(p.color) }}
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
                  <td className="px-5 py-3.5 text-xs" style={{ color: "var(--text-muted)" }}>
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
