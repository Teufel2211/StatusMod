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

  if (loading) return <div className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>$ loading profile…</div>

  if (!player) {
    return (
      <div className="card card-glow text-center py-12">
        <div className="font-display text-4xl mb-3" style={{ color: "var(--accent)", opacity: 0.5 }}>◎</div>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>Player not found</p>
      </div>
    )
  }

  const displayColor = cssColor(color)

  return (
    <div>
      <div className="mb-8 fade-up">
        <div className="kicker mb-3">Profile · {player.uuid.slice(0, 8)}</div>
        <div className="flex items-center gap-5">
          <div style={{ filter: "drop-shadow(0 0 18px var(--glow-accent))" }}>
            <PlayerAvatar uuid={player.uuid} username={player.username} avatar={player.avatar} sizePx={72} />
          </div>
          <div>
            <h1 className="font-display font-bold text-3xl text-glow" style={{ color: "var(--text-primary)" }}>
              {player.username ?? "Unknown Player"}
            </h1>
            <p className="text-xs font-mono mt-1" style={{ color: "var(--text-muted)" }}>{player.uuid}</p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
        <div className="card card-glow fade-up anim-d1">
          <div className="kicker mb-2" style={{ fontSize: "10px" }}>Status</div>
          <div className="flex items-center gap-2">
            {player.color && player.color !== "reset" && (
              <span className="w-3 h-3 rounded-full inline-block shrink-0" style={{ background: cssColor(player.color), boxShadow: `0 0 10px ${cssColor(player.color)}` }} />
            )}
            <span className="font-medium" style={{ color: "var(--text-primary)" }}>{player.status ?? "—"}</span>
          </div>
        </div>
        <div className="card fade-up anim-d2">
          <div className="kicker mb-2" style={{ fontSize: "10px" }}>Joined</div>
          <div className="font-mono text-sm" style={{ color: "var(--text-primary)" }}>{new Date(player.created_at).toLocaleDateString()}</div>
        </div>
        <div className="card fade-up anim-d3">
          <div className="kicker mb-2" style={{ fontSize: "10px" }}>Preview</div>
          <div className="flex items-center gap-2">
            <span className="w-3 h-3 rounded-full inline-block shrink-0" style={{ background: displayColor, boxShadow: `0 0 10px ${displayColor}` }} />
            <span className="text-sm truncate" style={{ color: "var(--text-secondary)" }}>{status || "—"}</span>
          </div>
        </div>
      </div>

      <div className="term-window fade-up anim-d2">
        <div className="term-bar">
          <span className="term-dot" style={{ backgroundColor: "#ef4444" }} />
          <span className="term-dot" style={{ backgroundColor: "#f59e0b" }} />
          <span className="term-dot" style={{ backgroundColor: "#10b981" }} />
          <span className="ml-2">edit --status</span>
        </div>
        <div className="p-6">
          <div className="space-y-4">
            <div>
              <label className="label font-mono text-xs uppercase tracking-widest">Status Text</label>
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
              <label className="label font-mono text-xs uppercase tracking-widest">Color</label>
              <div className="flex flex-wrap gap-2">
                {COLORS.map((c) => (
                  <button
                    key={c}
                    onClick={() => setColor(c)}
                    className={`w-7 h-7 rounded border-2 transition-all hover:scale-110 ${
                      color === c ? "scale-110" : ""
                    }`}
                    style={{
                      backgroundColor: cssColor(c),
                      borderColor: color === c ? "var(--accent)" : "var(--border)",
                      boxShadow: color === c ? "0 0 10px var(--glow-accent)" : undefined,
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
              >
                {saving ? "Saving..." : "Save"}
              </button>
              {saved && <span className="text-xs font-mono text-green-400">[OK] Saved!</span>}
              {error && <span className="text-xs font-mono text-red-400">[ERR] {error}</span>}
            </div>
          </div>
        </div>
      </div>
    </div>
  )
}
