"use client"

import { useEffect, useState } from "react"
import { apiFetch, getServerId } from "@/lib/client/auth"

export default function ConfigPage() {
  const [config, setConfig] = useState("")
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState("")
  const [fleetUrl, setFleetUrl] = useState("")
  const [fleetSecret, setFleetSecret] = useState("")
  const [fleetSaving, setFleetSaving] = useState(false)
  const [fleetMessage, setFleetMessage] = useState("")

  useEffect(() => {
    const serverId = getServerId()
    if (!serverId) return
    apiFetch(`/api/server/${serverId}/config`)
      .then((r) => r.json())
      .then((d) => setConfig(JSON.stringify(d, null, 2)))
      .catch(() => {})
    apiFetch(`/api/fleet/config`)
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!d) return
        if (typeof d.dashboard_url === "string") setFleetUrl(d.dashboard_url)
        if (typeof d.setup_secret === "string") setFleetSecret(d.setup_secret)
      })
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

  async function handleFleetSave() {
    setFleetSaving(true)
    setFleetMessage("")

    try {
      const res = await apiFetch(`/api/fleet/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dashboard_url: fleetUrl, setup_secret: fleetSecret }),
      })

      if (res.ok) setFleetMessage("Fleet config saved – servers pull it within ~60s")
      else setFleetMessage("Failed to save")
    } catch {
      setFleetMessage("Failed to save")
    } finally {
      setFleetSaving(false)
    }
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display mb-1" style={{ color: "var(--text-primary)" }}>Server Config</h1>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>Edit your server configuration (JSON)</p>
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

      <div className="card mt-6">
        <h2 className="text-lg font-display mb-1" style={{ color: "var(--text-primary)" }}>Fleet Config</h2>
        <p className="text-sm mb-4" style={{ color: "var(--text-muted)" }}>
          Verteilt Dashboard-URL + Setup-Secret an alle Server mit Fleet-Mod (Pull alle ~60s). Server-IDs und API-Keys bleiben je Server erhalten.
        </p>

        <label className="text-sm block mb-1" style={{ color: "var(--text-secondary)" }}>Dashboard URL (fleet)</label>
        <input
          className="input font-mono text-xs w-full"
          value={fleetUrl}
          onChange={(e) => setFleetUrl(e.target.value)}
          placeholder="https://statusmod-dashboard.vercel.app"
          spellCheck={false}
        />

        <label className="text-sm block mb-1 mt-4" style={{ color: "var(--text-secondary)" }}>Setup Secret (fleet)</label>
        <input
          className="input font-mono text-xs w-full"
          type="password"
          value={fleetSecret}
          onChange={(e) => setFleetSecret(e.target.value)}
          placeholder="Gleicher Wert für alle Server"
          spellCheck={false}
          autoComplete="new-password"
        />

        <div className="flex items-center gap-3 mt-4">
          <button className="btn-primary" onClick={handleFleetSave} disabled={fleetSaving}>
            {fleetSaving ? "Saving..." : "Save Fleet Config"}
          </button>
          {fleetMessage && (
            <span className={`text-sm ${fleetMessage.startsWith("Fleet config saved") ? "text-emerald-400" : "text-red-400"}`}>
              {fleetMessage}
            </span>
          )}
        </div>
      </div>
    </div>
  )
}
