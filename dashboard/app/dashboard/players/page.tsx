"use client"

import { useEffect, useState } from "react"
import { apiFetch, getServerId } from "@/lib/client/auth"

type Player = {
  id: number
  server_id: string
  uuid: string
  username: string | null
  status: string | null
  color: string | null
  created_at: string
}

export default function PlayersPage() {
  const [players, setPlayers] = useState<Player[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const serverId = getServerId()
    if (!serverId) { setLoading(false); return }

    apiFetch(`/api/players/${serverId}`)
      .then((r) => r.json())
      .then((d) => setPlayers(Array.isArray(d) ? d : []))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display text-[#e8e6e0] mb-1">Players</h1>
        <p className="text-sm text-[#636980]">{players.length} player(s) on your server</p>
      </div>

      {loading ? (
        <div className="text-sm text-[#636980] font-mono">Loading...</div>
      ) : players.length === 0 ? (
        <div className="card text-center py-12">
          <div className="text-3xl mb-3 opacity-30">◎</div>
          <p className="text-sm text-[#636980]">No players yet</p>
          <p className="text-xs text-[#636980] mt-1">Players will appear here when they join the server</p>
        </div>
      ) : (
        <div className="card p-0 overflow-hidden">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-[#2d3242]">
                <th className="text-left px-5 py-3 text-[#636980] font-medium text-xs uppercase tracking-wider">Username</th>
                <th className="text-left px-5 py-3 text-[#636980] font-medium text-xs uppercase tracking-wider">Status</th>
                <th className="text-left px-5 py-3 text-[#636980] font-medium text-xs uppercase tracking-wider">UUID</th>
                <th className="text-left px-5 py-3 text-[#636980] font-medium text-xs uppercase tracking-wider">Joined</th>
              </tr>
            </thead>
            <tbody>
              {players.map((p) => (
                <tr
                  key={p.id}
                  className="border-b border-[#2d3242]/50 last:border-0 hover:bg-[#23273a]/50 transition-colors cursor-pointer"
                  onClick={() => window.location.href = `/dashboard/players/${p.uuid}`}
                >
                  <td className="px-5 py-3.5">
                    <span className="text-[#e8e6e0] font-medium">{p.username ?? "—"}</span>
                  </td>
                  <td className="px-5 py-3.5">
                    {p.status ? (
                      <span className="inline-flex items-center gap-1.5">
                        {p.color && (
                          <span
                            className="w-2 h-2 rounded-full inline-block"
                            style={{ backgroundColor: p.color }}
                          />
                        )}
                        <span className="text-[#9ea3b3]">{p.status}</span>
                      </span>
                    ) : (
                      <span className="text-[#636980]">—</span>
                    )}
                  </td>
                  <td className="px-5 py-3.5">
                    <code className="text-xs text-[#636980] font-mono">{p.uuid.slice(0, 8)}...</code>
                  </td>
                  <td className="px-5 py-3.5 text-[#636980] text-xs">
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
