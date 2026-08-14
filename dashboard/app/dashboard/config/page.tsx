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
      <div className="mb-8">
        <h1 className="text-2xl font-display text-[#e8e6e0] mb-1">Server Config</h1>
        <p className="text-sm text-[#636980]">Edit your server configuration (JSON)</p>
      </div>

      <div className="card">
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
            <span className={`text-sm ${message === "Config saved" ? "text-emerald-400" : "text-red-400"}`}>
              {message}
            </span>
          )}
        </div>
      </div>
    </div>
  )
}
