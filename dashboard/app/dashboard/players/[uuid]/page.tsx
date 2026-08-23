"use client"

import { useEffect, useState, useCallback } from "react"
import { useParams } from "next/navigation"
import { apiFetch, getServerId } from "@/lib/client/auth"
import { cssColor } from "@/lib/color"
import { PlayerAvatar } from "@/components/player-avatar"

const COLORS = [
  "reset", "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
  "dark_purple", "gold", "gray", "dark_gray", "blue", "green",
  "aqua", "red", "light_purple", "yellow", "white",
]

type PlayerDetail = {
  id: number
  server_id: string
  uuid: string
  username: string | null
  status: string | null
  color: string | null
  avatar: string | null
  created_at: string
  updated_at: string | null
  anonymized: boolean | null
}

export default function PlayerDetailPage() {
  const { uuid } = useParams<{ uuid: string }>()
  const [player, setPlayer] = useState<PlayerDetail | null>(null)
  const [loading, setLoading] = useState(true)
  const [status, setStatus] = useState("")
  const [color, setColor] = useState("reset")
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)
  const [error, setError] = useState("")

  const load = useCallback(async () => {
    if (!uuid) return
    const serverId = getServerId()
    if (!serverId) return

    try {
      const r = await apiFetch(`/api/players/${serverId}/${uuid}`)
      const d = await r.json()
      setPlayer(d)
      setStatus(d?.status ?? "")
      setColor(d?.color ?? "reset")
    } catch {}
    setLoading(false)
  }, [uuid])

  useEffect(() => { load() }, [load])

  const save = async () => {
    const serverId = getServerId()
    if (!serverId || !uuid) return
    setSaving(true)
    setError("")
    setSaved(false)
    try {
      const r = await apiFetch(`/api/players/${serverId}/${uuid}/status`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ status, color }),
      })
      if (r.ok) {
        setSaved(true)
        setPlayer((p) => p ? { ...p, status, color } : p)
        setTimeout(() => setSaved(false), 2000)
      } else {
        const d = await r.json().catch(() => ({}))
        setError(d.error ?? "Failed to save")
      }
    } catch {
      setError("Network error")
    }
    setSaving(false)
  }

  if (loading) return <div className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>Loading...</div>

  if (!player) {
    return (
      <div className="card text-center py-12">
        <div className="text-3xl mb-3 opacity-30">◎</div>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>Player not found</p>
      </div>
    )
  }

  const displayColor = cssColor(color)

  return (
    <div>
      <div className="mb-8">
        <div className="flex items-center gap-4 mb-1">
          <PlayerAvatar uuid={player.uuid} username={player.username} avatar={player.avatar} sizePx={64} />
          <div>
            <h1 className="text-2xl font-display" style={{ color: "var(--text-primary)" }}>
              {player.username ?? "Unknown Player"}
            </h1>
            <p className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>{player.uuid}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
        <div className="card">
          <div className="text-xs font-medium mb-2" style={{ color: "var(--text-muted)" }}>Status</div>
          <div className="flex items-center gap-2">
            {player.color && player.color !== "reset" && (
              <span className="w-3 h-3 rounded-full inline-block" style={{ background: cssColor(player.color) }} />
            )}
            <span style={{ color: "var(--text-primary)" }}>{player.status ?? "—"}</span>
          </div>
        </div>
        <div className="card">
          <div className="text-xs font-medium mb-2" style={{ color: "var(--text-muted)" }}>Joined</div>
          <div style={{ color: "var(--text-primary)" }}>{new Date(player.created_at).toLocaleDateString()}</div>
        </div>
      </div>

      <div className="card">
        <div className="text-xs font-medium mb-4" style={{ color: "var(--text-muted)" }}>Edit Status</div>

        <div className="space-y-4">
          <div>
            <label className="text-xs block mb-1" style={{ color: "var(--text-muted)" }}>Status Text</label>
            <input
              type="text"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              maxLength={64}
              placeholder="AFK, Busy, Building..."
              className="input font-mono"
            />
          </div>

          <div>
            <label className="text-xs block mb-1" style={{ color: "var(--text-muted)" }}>Color</label>
            <div className="flex flex-wrap gap-2">
              {COLORS.map((c) => (
                <button
                  key={c}
                  onClick={() => setColor(c)}
                  className={`w-7 h-7 rounded border-2 transition-all ${
                    color === c ? "scale-110" : ""
                  }`}
                  style={{
                    backgroundColor: cssColor(c),
                    borderColor: color === c ? "var(--accent)" : "var(--border)",
                  }}
                  title={c}
                />
              ))}
              <input
                type="text"
                value={color.startsWith("#") ? color : ""}
                onChange={(e) => { const v = e.target.value; if (v.startsWith("#")) setColor(v) }}
                placeholder="#RRGGBB"
                className="w-24 rounded px-2 py-1 text-xs font-mono"
                style={{
                  backgroundColor: "var(--bg-input)",
                  border: "1px solid var(--border)",
                  color: "var(--text-primary)",
                }}
              />
            </div>
          </div>

          <div className="flex items-center gap-3 pt-2">
            <button
              onClick={save}
              disabled={saving}
              className="btn-primary text-sm px-4 py-2"
              style={{ backgroundColor: "#4a6fa5", color: "#fff" }}
            >
              {saving ? "Saving..." : "Save"}
            </button>
            {saved && <span className="text-xs text-green-400">Saved!</span>}
            {error && <span className="text-xs text-red-400">{error}</span>}
          </div>
        </div>
      </div>
    </div>
  )
}
