"use client"

import { useEffect, useState } from "react"
import { apiFetch } from "@/lib/client/auth"

export default function FleetPage() {
  const [fleetUrl, setFleetUrl] = useState("")
  const [fleetSecret, setFleetSecret] = useState("")
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState("")
  const [loaded, setLoaded] = useState(false)

  useEffect(() => {
    apiFetch(`/api/fleet/config`)
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!d) {
          setMessage("Kein Zugriff (Owner erforderlich).")
          return
        }
        if (typeof d.dashboard_url === "string") setFleetUrl(d.dashboard_url)
        if (typeof d.setup_secret === "string") setFleetSecret(d.setup_secret)
        setLoaded(true)
      })
      .catch(() => setMessage("Laden fehlgeschlagen."))
  }, [])

  async function handleSave() {
    setSaving(true)
    setMessage("")

    try {
      const res = await apiFetch(`/api/fleet/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dashboard_url: fleetUrl, setup_secret: fleetSecret }),
      })

      if (res.ok) setMessage("Fleet config gespeichert – Server mit Fleet-Mod ziehen sie binnen ~60s (Neustart erforderlich).")
      else setMessage("Speichern fehlgeschlagen.")
    } catch {
      setMessage("Speichern fehlgeschlagen.")
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-display mb-1" style={{ color: "var(--text-primary)" }}>Fleet Admin</h1>
        <p className="text-sm" style={{ color: "var(--text-muted)" }}>
          Zentrale Werte für alle Server mit Fleet-Mod. Server-IDs und API-Keys bleiben je Server erhalten. Die Fleet-Einstellungen liegen auf dem Server in einer eigenen Datei (<span className="font-mono">config/statusmodfleet/config.json</span>), nicht in der normalen Server-Config.
        </p>
      </div>

      <div className="card">
        <label className="text-sm block mb-1" style={{ color: "var(--text-secondary)" }}>Dashboard URL (fleet)</label>
        <input
          className="input font-mono text-xs w-full"
          value={fleetUrl}
          onChange={(e) => setFleetUrl(e.target.value)}
          placeholder="https://statusmod-dashboard.vercel.app"
          spellCheck={false}
          disabled={!loaded}
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
          disabled={!loaded}
        />

        <div className="flex items-center gap-3 mt-4">
          <button className="btn-primary" onClick={handleSave} disabled={saving || !loaded}>
            {saving ? "Saving..." : "Save Fleet Config"}
          </button>
          {message && (
            <span className={`text-sm ${message.startsWith("Fleet config gespeichert") ? "text-emerald-400" : "text-red-400"}`}>
              {message}
            </span>
          )}
        </div>
      </div>

      <div className="card mt-6">
        <h2 className="text-lg font-display mb-1" style={{ color: "var(--text-primary)" }}>Ablauf</h2>
        <ol className="text-sm list-decimal ml-5 space-y-1" style={{ color: "var(--text-secondary)" }}>
          <li>Fleet-Mod-JAR auf jedem Server in <span className="font-mono">mods</span> legen + Server neu starten.</li>
          <li>Werte hier setzen und speichern.</li>
          <li>Server ziehen sie automatisch (alle ~60s), Neustart übernimmt sie.</li>
          <li>Owner-Code für dieses Panel: <span className="font-mono">/fleet owner-code</span> in der Server-Konsole.</li>
        </ol>
      </div>
    </div>
  )
}
