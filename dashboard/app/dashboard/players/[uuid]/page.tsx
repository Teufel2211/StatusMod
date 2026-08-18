"use client"

import { useEffect, useState, useCallback } from "react"
import { useParams } from "next/navigation"
import { apiFetch, getServerId } from "@/lib/client/auth"

const COLORS = [
  "reset", "black", "dark_blue", "dark_green", "dark_aqua", "dark_red",
  "dark_purple", "gold", "gray", "dark_gray", "blue", "green",
  "aqua", "red", "light_purple", "yellow", "white",
]

const COLOR_HEX: Record<string, string> = {
  reset: "#e8e6e0", black: "#000000", dark_blue: "#0000AA",
  dark_green: "#00AA00", dark_aqua: "#00AAAA", dark_red: "#AA0000",
  dark_purple: "#AA00AA", gold: "#FFAA00", gray: "#AAAAAA",
  dark_gray: "#555555", blue: "#5555FF", green: "#55FF55",
  aqua: "#55FFFF", red: "#FF5555", light_purple: "#FF55FF",
  yellow: "#FFFF55", white: "#FFFFFF",
}

type PlayerDetail = {
  id: number
  server_id: string
  uuid: string
  username: string | null
  status: string | null
  color: string | null
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

  if (loading) return <div className="text-sm text-[#636980] font-mono">Loading...</div>

  if (!player) {
    return (
      <div className="card text-center py-12">
        <div className="text-3xl mb-3 opacity-30">◎</div>
        <p className="text-sm text-[#636980]">Player not found</p>
      </div>
    )
  }

  const displayColor = color in COLOR_HEX ? COLOR_HEX[color] : color

  return (
    <div>
      <div className="mb-8">
        <div className="flex items-center gap-4 mb-1">
          <img
            src={`https://mc-heads.net/avatar/${player.uuid}/64`}
            alt=""
            className="w-16 h-16 rounded-lg"
            onError={(e) => { (e.target as HTMLImageElement).style.display = "none" }}
          />
          <div>
            <h1 className="text-2xl font-display text-[#e8e6e0]">
              {player.username ?? "Unknown Player"}
            </h1>
            <p className="text-sm text-[#636980] font-mono">{player.uuid}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mb-8">
        <div className="card">
          <div className="text-xs text-[#636980] font-medium mb-2">Status</div>
          <div className="flex items-center gap-2">
            {player.color && player.color !== "reset" && (
              <span className="w-3 h-3 rounded-full inline-block" style={{ backgroundColor: COLOR_HEX[player.color] ?? player.color }} />
            )}
            <span className="text-[#e8e6e0]">{player.status ?? "—"}</span>
          </div>
        </div>
        <div className="card">
          <div className="text-xs text-[#636980] font-medium mb-2">Joined</div>
          <div className="text-[#e8e6e0]">{new Date(player.created_at).toLocaleDateString()}</div>
        </div>
      </div>

      <div className="card">
        <div className="text-xs text-[#636980] font-medium mb-4">Edit Status</div>

        <div className="space-y-4">
          <div>
            <label className="text-xs text-[#636980] block mb-1">Status Text</label>
            <input
              type="text"
              value={status}
              onChange={(e) => setStatus(e.target.value)}
              maxLength={64}
              placeholder="AFK, Busy, Building..."
              className="w-full bg-[#1a1a2e] border border-[#2a2a3e] rounded px-3 py-2 text-sm text-[#e8e6e0] placeholder-[#4a4a5e] focus:outline-none focus:border-[#4a6fa5]"
            />
          </div>

          <div>
            <label className="text-xs text-[#636980] block mb-1">Color</label>
            <div className="flex flex-wrap gap-2">
              {COLORS.map((c) => (
                <button
                  key={c}
                  onClick={() => setColor(c)}
                  className={`w-7 h-7 rounded border-2 transition-all ${
                    color === c ? "border-[#4a6fa5] scale-110" : "border-[#2a2a3e]"
                  }`}
                  style={{ backgroundColor: COLOR_HEX[c] ?? c }}
                  title={c}
                />
              ))}
              <input
                type="text"
                value={color.startsWith("#") ? color : ""}
                onChange={(e) => { const v = e.target.value; if (v.startsWith("#")) setColor(v) }}
                placeholder="#RRGGBB"
                className="w-24 bg-[#1a1a2e] border border-[#2a2a3e] rounded px-2 py-1 text-xs text-[#e8e6e0] placeholder-[#4a4a5e] focus:outline-none focus:border-[#4a6fa5]"
              />
            </div>
          </div>

          <div className="flex items-center gap-3 pt-2">
            <button
              onClick={save}
              disabled={saving}
              className="px-4 py-2 bg-[#4a6fa5] hover:bg-[#3a5f95] disabled:opacity-50 text-white text-sm rounded transition-colors"
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
