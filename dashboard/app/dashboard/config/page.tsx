"use client"

import { useEffect, useState } from "react"
import { apiFetch, getServerId } from "@/lib/client/auth"

export default function ConfigPage() {
  const [config, setConfig] = useState("")
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState("")

  useEffect(() => {
    const serverId = getServerId()
    if (!serverId) return
    apiFetch(`/api/server/${serverId}/config`)
      .then((r) => r.json())
      .then((d) => setConfig(JSON.stringify(d, null, 2)))
      .catch(() => {})
  }, [])

  async function handleSave() {
    setSaving(true)
    setMessage("")

    try {
      const serverId = getServerId()
      if (!serverId) {
        setMessage("Not logged in")
        return
      }
      const parsed = JSON.parse(config)
      const res = await apiFetch(`/api/server/${serverId}/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ config: parsed }),
      })

      if (res.ok) setMessage("Config saved")
      else setMessage("Failed to save")
    } catch {
      setMessage("Invalid JSON")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <div className="mb-8 fade-up">
        <div className="kicker mb-2">Raw JSON · validiert beim Speichern</div>
        <h1 className="font-display font-bold text-3xl text-glow" style={{ color: "var(--text-primary)" }}>Server Config</h1>
        <p className="text-sm mt-1" style={{ color: "var(--text-muted)" }}>Edit your server configuration (JSON)</p>
      </div>

      <div className="term-window fade-up anim-d1">
        <div className="term-bar">
          <span className="term-dot" style={{ backgroundColor: "#ef4444" }} />
          <span className="term-dot" style={{ backgroundColor: "#f59e0b" }} />
          <span className="term-dot" style={{ backgroundColor: "#10b981" }} />
          <span className="ml-2">config.json — vorsichtig editieren</span>
        </div>
        <div className="p-6">
          <textarea
            className="input font-mono text-xs min-h-[300px] resize-y"
            value={config}
            onChange={(e) => setConfig(e.target.value)}
            spellCheck={false}
          />

          <div className="flex items-center gap-3 mt-4">
            <button className="btn-primary" onClick={handleSave} disabled={saving}>
              {saving ? "Saving..." : "Save Config"}
            </button>
            {message && (
              <span className={`text-sm font-mono ${message === "Config saved" ? "text-emerald-400" : "text-red-400"}`}>
                {message === "Config saved" ? "[OK] Config saved" : message}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
