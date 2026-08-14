"use client"

import { useEffect, useState } from "react"
import { useParams } from "next/navigation"
import { apiFetch, getServerId } from "@/lib/client/auth"

type PlayerDetail = {
  id: number
  server_id: string
  uuid: string
  username: string | null
  status: string | null
  color: string | null
  created_at: string
  anonymized: boolean | null
}

export default function PlayerDetailPage() {
  const { uuid } = useParams<{ uuid: string }>()
  const [player, setPlayer] = useState<PlayerDetail | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    if (!uuid) return
    const serverId = getServerId()
    if (!serverId) return

    apiFetch(`/api/players/${serverId}/${uuid}`)
      .then((r) => r.json())
      .then((d) => setPlayer(d))
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [uuid])

  if (loading) return <div className="text-sm text-[#636980] font-mono">Loading...</div>

  if (!player) {
    return (
      <div className="card text-center py-12">
        <div className="text-3xl mb-3 opacity-30">◎</div>
        <p className="text-sm text-[#636980]">Player not found</p>
      </div>
    )
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display text-[#e8e6e0] mb-1">
          {player.username ?? "Unknown Player"}
        </h1>
        <p className="text-sm text-[#636980] font-mono">{player.uuid}</p>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
        <div className="card">
          <div className="text-xs text-[#636980] font-medium mb-2">Status</div>
          <div className="flex items-center gap-2">
            {player.color && (
              <span className="w-3 h-3 rounded-full inline-block" style={{ backgroundColor: player.color }} />
            )}
            <span className="text-[#e8e6e0]">{player.status ?? "—"}</span>
          </div>
        </div>
        <div className="card">
          <div className="text-xs text-[#636980] font-medium mb-2">Joined</div>
          <div className="text-[#e8e6e0]">{new Date(player.created_at).toLocaleDateString()}</div>
        </div>
      </div>
    </div>
  )
}
