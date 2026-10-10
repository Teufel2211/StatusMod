"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import { apiFetch, getServerId, ensureSession } from "@/lib/client/auth"
import { useRealtimePlayers } from "@/lib/client/use-realtime"
import { PlayerAvatar } from "@/components/player-avatar"
import FleetTopbar from "@/components/fleet-topbar"
import Footer from "@/components/footer"

type HealthData = {
  status: string
  database: string
  timestamp: string
}

type AuditEntry = {
  id: number
  server_id: string
  action: string
  detail: string | null
  created_at: string
}

type FleetServer = {
  id: string
  created_at: string | null
  known: number | null
  online: number | null
}

type ApiKeyRow = {
  id: number
  key_prefix: string
  scopes: string[]
  created_at: string
  expires_at: string
  revoked: boolean
}

type PresetRow = {
  name: string
  server_id: string
  status: string | null
  color: string | null
  creator_uuid: string | null
}

type HistoryRow = {
  id: number
  dashboard_url: string
  has_secret: boolean
  created_at: string | null
}

type Alert = {
  level: "error" | "warn" | "info"
  text: string
}

function ageString(iso: string | null): string {
  if (!iso) return "noch nie"
  const s = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000))
  if (s < 60) return `vor ${s} Sek.`
  const m = Math.floor(s / 60)
  if (m < 60) return `vor ${m} Min.`
  const h = Math.floor(m / 60)
  if (h < 48) return `vor ${h} Std.`
  return `vor ${Math.floor(h / 24)} Tg.`
}

function actionColor(action: string): string {
  const a = action.toLowerCase()
  if (a.includes("mute") || a.includes("block") || a.includes("ban")) return "#ff5f5f"
  if (a.includes("fleet") || a.includes("config") || a.includes("key")) return "var(--accent)"
  if (a.includes("login") || a.includes("auth") || a.includes("code")) return "var(--cyan)"
  return "var(--text-secondary)"
}

const SECTIONS: Array<[string, string]> = [
  ["warnungen", "Warnungen"],
  ["uebersicht", "Übersicht"],
  ["server", "Server"],
  ["spieler", "Spieler"],
  ["config", "Config & Presets"],
  ["verteilen", "Verteilung"],
  ["ids", "IDs & Keys"],
  ["protokoll", "Protokoll"],
]

function SectionHead({ id, kicker, title, sub }: { id: string; kicker: string; title: string; sub?: string }) {
  return (
    <div id={id} className="mb-4 scroll-mt-24">
      <div className="kicker mb-1">{kicker}</div>
      <h2 className="font-display font-bold text-2xl" style={{ color: "var(--text-primary)" }}>{title}</h2>
      {sub && <p className="text-sm mt-1" style={{ color: "var(--text-muted)" }}>{sub}</p>}
    </div>
  )
}

export default function FleetAdminPage() {
  const router = useRouter()
  const [authed, setAuthed] = useState(false)
  const [checking, setChecking] = useState(true)
  const [health, setHealth] = useState<HealthData | null>(null)
  const [knownCount, setKnownCount] = useState<number | null>(null)
  const [activity, setActivity] = useState<AuditEntry[]>([])
  const [servers, setServers] = useState<FleetServer[]>([])
  const [keys, setKeys] = useState<ApiKeyRow[]>([])
  const [presets, setPresets] = useState<PresetRow[]>([])
  const [history, setHistory] = useState<HistoryRow[]>([])
  const [fleetUrl, setFleetUrl] = useState("")
  const [fleetSecret, setFleetSecret] = useState("")
  const [fleetUpdatedAt, setFleetUpdatedAt] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState("")
  const [loaded, setLoaded] = useState(false)
  const [serverId, setServerId] = useState("")
  const [copiedId, setCopiedId] = useState("")
  const [presetName, setPresetName] = useState("")
  const [presetStatus, setPresetStatus] = useState("")
  const [presetColor, setPresetColor] = useState("reset")
  const [presetMsg, setPresetMsg] = useState("")
  const [restoreId, setRestoreId] = useState<number | null>(null)
  const { players: livePlayers, connection } = useRealtimePlayers()

  useEffect(() => {
    let cancelled = false
    ensureSession().then((ok) => {
      if (cancelled) return
      if (ok) {
        setAuthed(true)
        setChecking(false)
      } else {
        router.push("/login")
      }
    })
    return () => {
      cancelled = true
    }
  }, [router])

  useEffect(() => {
    if (!authed) return
    fetch("/api/health")
      .then((r) => r.json())
      .then((d) => setHealth(d))
      .catch(() => {})
    const sid = getServerId()
    if (sid) {
      setServerId(sid)
      apiFetch(`/api/players/${sid}`)
        .then((r) => (r.ok ? r.json() : []))
        .then((d) => setKnownCount(Array.isArray(d) ? d.length : null))
        .catch(() => {})
      apiFetch(`/api/audit/${sid}`)
        .then((r) => (r.ok ? r.json() : []))
        .then((d) => setActivity(Array.isArray(d) ? d.slice(0, 10) : []))
        .catch(() => {})
      loadPresets(sid)
    }
    apiFetch(`/api/fleet/config`)
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!d) {
          setMessage("Kein Zugriff – nur für Owner.")
          return
        }
        if (typeof d.dashboard_url === "string") setFleetUrl(d.dashboard_url)
        if (typeof d.setup_secret === "string") setFleetSecret(d.setup_secret)
        if (typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
        setLoaded(true)
      })
      .catch(() => setMessage("Laden fehlgeschlagen."))
    apiFetch(`/api/servers`)
      .then((r) => (r.ok ? r.json() : []))
      .then((d) => setServers(Array.isArray(d) ? d : []))
      .catch(() => {})
    apiFetch(`/api/keys`)
      .then((r) => (r.ok ? r.json() : []))
      .then((d) => setKeys(Array.isArray(d) ? d : []))
      .catch(() => {})
    loadHistory()
  }, [authed])

  function loadPresets(sid: string) {
    apiFetch(`/api/presets/${sid}`)
      .then((r) => (r.ok ? r.json() : []))
      .then((d) => setPresets(Array.isArray(d) ? d : []))
      .catch(() => {})
  }

  function loadHistory() {
    apiFetch(`/api/fleet/history`)
      .then((r) => (r.ok ? r.json() : []))
      .then((d) => setHistory(Array.isArray(d) ? d : []))
      .catch(() => {})
  }

  async function handleSave() {
    setSaving(true)
    setMessage("")

    try {
      const res = await apiFetch(`/api/fleet/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ dashboard_url: fleetUrl, setup_secret: fleetSecret }),
      })

      if (res.ok) {
        setMessage("Gespeichert. Die Server übernehmen es in ca. 1 Minute (Neustart erforderlich).")
        apiFetch(`/api/fleet/config`)
          .then((r) => (r.ok ? r.json() : null))
          .then((d) => {
            if (d && typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
          })
          .catch(() => {})
        loadHistory()
      } else setMessage("Speichern fehlgeschlagen.")
    } catch {
      setMessage("Speichern fehlgeschlagen.")
    } finally {
      setSaving(false)
    }
  }

  async function handleRollback(id: number) {
    if (restoreId !== id) {
      setRestoreId(id)
      setTimeout(() => setRestoreId((cur) => (cur === id ? null : cur)), 6000)
      return
    }
    setRestoreId(null)
    setSaving(true)
    setMessage("")
    try {
      const res = await apiFetch(`/api/fleet/config`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ restore_id: id }),
      })
      if (res.ok) {
        setMessage("Zurückgesetzt. Die Server übernehmen es in ca. 1 Minute (Neustart erforderlich).")
        const r = await apiFetch(`/api/fleet/config`)
        if (r.ok) {
          const d = await r.json()
          if (typeof d.dashboard_url === "string") setFleetUrl(d.dashboard_url)
          if (typeof d.setup_secret === "string") setFleetSecret(d.setup_secret)
          if (typeof d.updated_at === "string") setFleetUpdatedAt(d.updated_at)
        }
        loadHistory()
      } else setMessage("Zurücksetzen fehlgeschlagen.")
    } catch {
      setMessage("Zurücksetzen fehlgeschlagen.")
    } finally {
      setSaving(false)
    }
  }

  async function handlePresetAdd(e: React.FormEvent) {
    e.preventDefault()
    if (!serverId) return
    setPresetMsg("")
    try {
      const res = await apiFetch(`/api/presets/${serverId}`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: presetName, status: presetStatus, color: presetColor }),
      })
      if (res.ok) {
        setPresetMsg("Preset gespeichert.")
        setPresetName("")
        setPresetStatus("")
        loadPresets(serverId)
      } else setPresetMsg("Speichern fehlgeschlagen.")
    } catch {
      setPresetMsg("Speichern fehlgeschlagen.")
    }
  }

  async function handlePresetDelete(name: string) {
    if (!serverId) return
    try {
      await apiFetch(`/api/presets/${serverId}/${encodeURIComponent(name)}`, { method: "DELETE" })
      loadPresets(serverId)
    } catch {}
  }

  function copyId(id: string) {
    try {
      navigator.clipboard.writeText(id)
      setCopiedId(id)
      setTimeout(() => setCopiedId((cur) => (cur === id ? "" : cur)), 2000)
    } catch {}
  }

  if (checking) {
    return (
      <div className="fleet-theme min-h-screen flex items-center justify-center" style={{ backgroundColor: "var(--bg-primary)" }}>
        <div className="flex items-center gap-3">
          <div className="w-2 h-2 rounded-full animate-pulse" style={{ backgroundColor: "var(--accent)" }} />
          <span className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>Verbindung wird aufgebaut…</span>
        </div>
      </div>
    )
  }

  if (!authed) return null

  const fleetActive = fleetSecret !== "" || fleetUrl !== ""
  const live = connection === "connected"
  const activeKeys = keys.filter((k) => !k.revoked)
  const expiringKeys = activeKeys.filter((k) => {
    if (!k.expires_at) return false
    const ms = new Date(k.expires_at).getTime() - Date.now()
    return ms > 0 && ms < 7 * 24 * 60 * 60 * 1000
  })

  const alerts: Alert[] = []
  if (health && health.status !== "healthy") {
    alerts.push({ level: "error", text: `System meldet: ${health.status} – Dashboard prüfen.` })
  }
  if (loaded && !fleetActive) {
    alerts.push({ level: "warn", text: "Fleet ist leer – keine Verteilung aktiv. Unten Werte setzen." })
  }
  if (loaded && activeKeys.length === 0) {
    alerts.push({ level: "warn", text: "Kein aktiver API-Key – Mods können nicht synchronisieren." })
  }
  expiringKeys.forEach((k) => {
    alerts.push({ level: "warn", text: `API-Key ${k.key_prefix}… läuft bald ab (${new Date(k.expires_at).toLocaleDateString()}).` })
  })
  if (knownCount === 0) {
    alerts.push({ level: "info", text: "Noch keine Spieler bekannt – kommen beim ersten Join." })
  }

  return (
    <div className="fleet-theme min-h-screen hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)", color: "var(--text-primary)" }}>
      <FleetTopbar />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6">
        <div className="mb-6 fade-up">
          <div className="kicker mb-2">Zentrale Steuerung · alle Server</div>
          <h1 className="font-display font-bold text-3xl sm:text-4xl fleet-phosphor">
            FLEET<span className="fleet-blink">_</span>COMMAND
          </h1>
        </div>

        <nav className="sticky top-[64px] z-10 mb-8 fade-up anim-d1">
          <div className="flex gap-2 overflow-x-auto py-2 px-3 rounded-xl" style={{ border: "1px solid var(--border)", backgroundColor: "color-mix(in srgb, var(--bg-primary) 90%, transparent)", backdropFilter: "blur(12px)" }}>
            {SECTIONS.map(([id, label]) => (
              <a
                key={id}
                href={`#${id}`}
                className="font-mono text-xs px-3 py-1.5 rounded-lg whitespace-nowrap transition-all"
                style={{ color: "var(--text-secondary)", border: "1px solid transparent" }}
                onMouseEnter={(e) => { e.currentTarget.style.color = "var(--accent)"; e.currentTarget.style.borderColor = "var(--accent-border)" }}
                onMouseLeave={(e) => { e.currentTarget.style.color = "var(--text-secondary)"; e.currentTarget.style.borderColor = "transparent" }}
              >
                {label}
              </a>
            ))}
          </div>
        </nav>

        <section className="mb-8">
          <SectionHead id="warnungen" kicker="Achtung" title="Warnungen & Ereignisse" sub={`${alerts.length} offen`} />
          {alerts.length === 0 ? (
            <div className="card fade-up flex items-center gap-3">
              <span className="badge-green">OK</span>
              <span className="text-sm" style={{ color: "var(--text-secondary)" }}>Alles ruhig – keine Warnungen.</span>
            </div>
          ) : (
            <div className="space-y-2">
              {alerts.map((a, i) => (
                <div
                  key={i}
                  className="card fade-up !py-3 !px-4 flex items-center gap-3"
                  style={a.level === "error"
                    ? { borderColor: "rgba(255,95,95,0.45)" }
                    : a.level === "warn"
                      ? { borderColor: "var(--accent-border)" }
                      : undefined}
                >
                  <span className={a.level === "error" ? "badge-red" : a.level === "warn" ? "badge-yellow" : "badge-green"}>
                    {a.level === "error" ? "!" : a.level === "warn" ? "?" : "i"}
                  </span>
                  <span className="text-sm" style={{ color: "var(--text-primary)" }}>{a.text}</span>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="mb-8">
          <SectionHead id="uebersicht" kicker="Live" title="Übersicht & Status" />
          <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
            {[
              { label: "Server", value: health?.status === "healthy" ? "Online" : "…", ok: health?.status === "healthy" ? true : null as boolean | null },
              { label: "Spieler online", value: String(livePlayers.length), ok: (livePlayers.length > 0 ? true : null) as boolean | null, sub: knownCount !== null ? `${knownCount} bekannt` : undefined },
              { label: "Live-Feed", value: live ? "Verbunden" : connection === "connecting" ? "…" : "Getrennt", ok: live ? true : connection === "connecting" ? null : false },
              { label: "Datenbank", value: health?.database === "connected" ? "Ok" : "…", ok: health?.database === "connected" ? true : null },
            ].map((s, i) => (
              <div key={s.label} className={`card fade-up anim-d${i + 1} !p-5`}>
                <div className="kicker mb-2" style={{ fontSize: "10px" }}>{s.label}</div>
                <div className="flex items-center gap-2">
                  <span className="stat-num" style={{ fontSize: "1.5rem" }}>{s.value}</span>
                  {s.ok === true && <span className="badge-green">OK</span>}
                  {s.ok === false && <span className="badge-red">!</span>}
                </div>
                {s.sub && <div className="text-[11px] font-mono mt-1" style={{ color: "var(--text-muted)" }}>{s.sub}</div>}
              </div>
            ))}
          </div>
        </section>

        <section className="mb-8">
          <SectionHead id="server" kicker="Flotte" title="Fleet-Server & Details" sub={`${servers.length} Server`} />
          {servers.length === 0 ? (
            <div className="card fade-up">
              <p className="text-sm" style={{ color: "var(--text-muted)" }}>Keine Server gefunden.</p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {servers.map((s, i) => (
                <div key={s.id} className={`card card-glow fade-up anim-d${(i % 4) + 1}`}>
                  <div className="flex items-center justify-between gap-2 mb-3">
                    <code className="text-sm font-mono font-bold" style={{ color: "var(--accent)" }}>{s.id.slice(0, 8)}…</code>
                    <button
                      onClick={() => copyId(s.id)}
                      className="font-mono text-[11px] px-2.5 py-1 rounded-lg transition-all"
                      style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}
                    >
                      {copiedId === s.id ? "kopiert!" : "ID kopieren"}
                    </button>
                  </div>
                  <div className="font-mono text-[11px] break-all mb-3" style={{ color: "var(--text-muted)" }}>{s.id}</div>
                  <div className="flex gap-4 font-mono text-xs" style={{ color: "var(--text-secondary)" }}>
                    <span><span className="pulse-dot mr-1.5" />{s.online ?? "?"} online</span>
                    <span>{s.known ?? "?"} bekannt</span>
                    <span>seit {s.created_at ? new Date(s.created_at).toLocaleDateString() : "?"}</span>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="mb-8">
          <SectionHead id="spieler" kicker="Roster" title="Spieler" sub={livePlayers.length > 0 ? `${livePlayers.length} online` : "Niemand online"} />
          <div className="card card-glow fade-up">
            {livePlayers.length === 0 ? (
              <p className="text-sm" style={{ color: "var(--text-muted)" }}>Niemand online. <a href="/dashboard/players" className="underline underline-offset-2" style={{ color: "var(--accent)" }}>Zum Spieler-Roster →</a></p>
            ) : (
              <>
                <div className="flex flex-wrap gap-2 mb-4">
                  {livePlayers.map((p) => (
                    <a
                      key={p.uuid}
                      href={`/dashboard/players/${p.uuid}`}
                      className="flex items-center gap-2 px-3 py-2 rounded-xl transition-all text-sm hover:-translate-y-0.5"
                      style={{ border: "1px solid var(--border)", backgroundColor: "var(--bg-primary)" }}
                      onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--accent-border)"; e.currentTarget.style.boxShadow = "0 0 14px var(--glow-accent)" }}
                      onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.boxShadow = "" }}
                    >
                      <PlayerAvatar uuid={p.uuid} username={p.username} avatar={p.avatar} sizePx={20} />
                      <span style={{ color: "var(--text-primary)" }}>{p.username ?? "Unknown"}</span>
                      {p.status && (
                        <span className="text-xs" style={{ color: "var(--text-muted)" }}>[{p.status}]</span>
                      )}
                    </a>
                  ))}
                </div>
                <a href="/dashboard/players" className="font-mono text-xs underline underline-offset-2" style={{ color: "var(--accent)" }}>
                  Alle Spieler verwalten (Status setzen, Details) →
                </a>
              </>
            )}
          </div>
        </section>

        <section className="mb-8">
          <SectionHead id="config" kicker="Inhalte" title="Konfiguration & Presets" sub="Server-JSON + Status-Presets" />
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="card fade-up">
              <h3 className="font-display font-semibold mb-1" style={{ color: "var(--text-primary)" }}>Server-Config</h3>
              <p className="text-xs mb-3" style={{ color: "var(--text-muted)" }}>JSON direkt editieren (Toggles, Farben, Cooldowns…).</p>
              <a href="/dashboard/config" className="btn-secondary text-xs px-4 py-2">Config öffnen →</a>
            </div>
            <div className="card card-glow fade-up anim-d1">
              <h3 className="font-display font-semibold mb-3" style={{ color: "var(--text-primary)" }}>Presets ({presets.length})</h3>
              {presets.length > 0 && (
                <div className="space-y-1.5 mb-4 max-h-44 overflow-auto">
                  {presets.map((p) => (
                    <div key={p.name} className="flex items-center gap-2 py-1.5 border-b last:border-0" style={{ borderColor: "var(--border)" }}>
                      <code className="text-xs font-mono font-bold" style={{ color: "var(--accent)" }}>{p.name}</code>
                      <span className="text-xs truncate flex-1" style={{ color: "var(--text-secondary)" }}>{p.status ?? "—"}</span>
                      <button
                        onClick={() => handlePresetDelete(p.name)}
                        className="font-mono text-[11px] transition-colors"
                        style={{ color: "var(--text-muted)" }}
                        onMouseEnter={(e) => { e.currentTarget.style.color = "#ff5f5f" }}
                        onMouseLeave={(e) => { e.currentTarget.style.color = "var(--text-muted)" }}
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              )}
              <form onSubmit={handlePresetAdd} className="grid grid-cols-3 gap-2">
                <input value={presetName} onChange={(e) => setPresetName(e.target.value)} placeholder="Name" maxLength={32} className="input text-xs" spellCheck={false} />
                <input value={presetStatus} onChange={(e) => setPresetStatus(e.target.value)} placeholder="Status" maxLength={64} className="input text-xs" spellCheck={false} />
                <input value={presetColor} onChange={(e) => setPresetColor(e.target.value)} placeholder="Farbe" maxLength={32} className="input text-xs" spellCheck={false} />
                <button type="submit" className="btn-primary text-xs px-3 py-2 col-span-3">Preset speichern</button>
              </form>
              {presetMsg && (
                <p className="text-xs font-mono mt-2" style={{ color: presetMsg.includes("gespeichert") ? "var(--accent)" : "#ff5f5f" }}>{presetMsg}</p>
              )}
            </div>
          </div>
        </section>

        <section className="mb-8">
          <SectionHead id="verteilen" kicker="Fleet" title="Verteilung & Rollbacks" sub={fleetUpdatedAt ? `Stand ${ageString(fleetUpdatedAt)}` : "Noch nie verteilt"} />
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="card card-glow fade-up">
              <label className="label">Dashboard-URL</label>
              <input
                className="input font-mono text-xs w-full"
                value={fleetUrl}
                onChange={(e) => setFleetUrl(e.target.value)}
                placeholder="https://statusmod-dashboard.vercel.app"
                spellCheck={false}
                disabled={!loaded}
              />
              <label className="label mt-4">Setup-Secret</label>
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
              <button className="btn-primary w-full mt-4 text-sm" onClick={handleSave} disabled={saving || !loaded}>
                {saving ? "Wird verteilt…" : "Auf alle Server verteilen"}
              </button>
              {message && (
                <p className="text-xs mt-3 font-mono" style={{ color: message.startsWith("Gespeichert") || message.startsWith("Zurückgesetzt") ? "var(--accent)" : "#ff5f5f" }}>
                  {message}
                </p>
              )}
              <p className="text-[11px] font-mono mt-3" style={{ color: "var(--text-muted)" }}>
                Datei: config/statusmodfleet/config.json · ca. 1 Minute + Neustart
              </p>
            </div>
            <div className="card fade-up anim-d1">
              <h3 className="font-display font-semibold mb-1" style={{ color: "var(--text-primary)" }}>Verlauf & Rollback</h3>
              <p className="text-xs mb-3" style={{ color: "var(--text-muted)" }}>Jede Verteilung wird gespeichert. Zweimal klicken zum Zurücksetzen.</p>
              {history.length === 0 ? (
                <p className="text-sm" style={{ color: "var(--text-muted)" }}>Noch keine Verteilungen.</p>
              ) : (
                <div className="space-y-2 max-h-72 overflow-auto">
                  {history.map((h) => (
                    <div key={h.id} className="flex items-center gap-3 py-2 border-b last:border-0" style={{ borderColor: "var(--border)" }}>
                      <span className="font-mono text-xs font-bold shrink-0" style={{ color: "var(--accent)" }}>#{h.id}</span>
                      <div className="flex-1 min-w-0">
                        <div className="text-xs truncate" style={{ color: "var(--text-secondary)" }}>{h.dashboard_url || "(leere URL)"}</div>
                        <div className="font-mono text-[10px]" style={{ color: "var(--text-muted)" }}>
                          {h.created_at ? new Date(h.created_at).toLocaleString() : "—"} · Secret gesetzt
                        </div>
                      </div>
                      <button
                        onClick={() => handleRollback(h.id)}
                        disabled={saving}
                        className="font-mono text-[11px] px-2.5 py-1.5 rounded-lg transition-all shrink-0"
                        style={restoreId === h.id
                          ? { border: "1px solid #ff5f5f", color: "#ff5f5f", backgroundColor: "rgba(255,95,95,0.1)" }
                          : { border: "1px solid var(--border)", color: "var(--text-muted)" }}
                      >
                        {restoreId === h.id ? "Sicher?" : "↩"}
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </section>

        <section className="mb-8">
          <SectionHead id="ids" kicker="Identität" title="Server-IDs & API-Schlüssel" />
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
            <div className="card fade-up">
              <h3 className="font-display font-semibold mb-3" style={{ color: "var(--text-primary)" }}>Server-IDs ({servers.length})</h3>
              {servers.length === 0 ? (
                <p className="text-sm" style={{ color: "var(--text-muted)" }}>Keine Server gefunden.</p>
              ) : (
                <div className="space-y-2">
                  {servers.map((s) => (
                    <div key={s.id} className="flex items-center gap-2 py-1.5 border-b last:border-0" style={{ borderColor: "var(--border)" }}>
                      <code className="text-xs font-mono break-all flex-1" style={{ color: "var(--text-secondary)" }}>{s.id}</code>
                      <button
                        onClick={() => copyId(s.id)}
                        className="font-mono text-[11px] px-2 py-1 rounded-lg shrink-0 transition-all"
                        style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}
                      >
                        {copiedId === s.id ? "kopiert!" : "kopieren"}
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
            <div className="card card-glow fade-up anim-d1">
              <h3 className="font-display font-semibold mb-1" style={{ color: "var(--text-primary)" }}>API-Schlüssel</h3>
              <p className="text-xs mb-3" style={{ color: "var(--text-muted)" }}>
                {activeKeys.length} aktiv{expiringKeys.length > 0 ? ` · ${expiringKeys.length} läuft bald ab` : ""}
              </p>
              {keys.length === 0 ? (
                <p className="text-sm" style={{ color: "var(--text-muted)" }}>Keine Keys vorhanden.</p>
              ) : (
                <div className="space-y-1.5 mb-3">
                  {keys.slice(0, 5).map((k) => (
                    <div key={k.id} className="flex items-center gap-2 font-mono text-xs">
                      <code style={{ color: "var(--text-secondary)" }}>{k.key_prefix}…</code>
                      <span className={k.revoked ? "badge-red" : "badge-green"}>{k.revoked ? "aus" : "an"}</span>
                      {k.expires_at && (
                        <span style={{ color: "var(--text-muted)" }}>bis {new Date(k.expires_at).toLocaleDateString()}</span>
                      )}
                    </div>
                  ))}
                </div>
              )}
              <a href="/dashboard/keys" className="btn-secondary text-xs px-4 py-2">Keys verwalten →</a>
            </div>
          </div>
        </section>

        <section className="mb-4">
          <SectionHead id="protokoll" kicker="Nachweis" title="Audit-Protokoll" />
          <div className="card fade-up">
            <div className="flex items-center justify-between mb-3">
              <span className="font-mono text-xs" style={{ color: "var(--text-muted)" }}>Neueste {activity.length} Einträge</span>
              <a href="/dashboard/audit" className="font-mono text-xs underline underline-offset-2" style={{ color: "var(--accent)" }}>
                Vollständiges Protokoll →
              </a>
            </div>
            {activity.length === 0 ? (
              <p className="text-sm" style={{ color: "var(--text-muted)" }}>Noch keine Einträge.</p>
            ) : (
              <div className="space-y-1.5">
                {activity.map((entry) => (
                  <div key={entry.id} className="flex items-baseline gap-3 text-xs sm:text-sm">
                    <span className="shrink-0 tabular-nums" style={{ color: "var(--text-muted)" }}>
                      {new Date(entry.created_at).toLocaleString()}
                    </span>
                    <span className="shrink-0 font-bold font-mono" style={{ color: actionColor(entry.action) }}>
                      [{entry.action}]
                    </span>
                    {entry.detail && (
                      <span className="truncate" style={{ color: "var(--text-secondary)" }}>{entry.detail}</span>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </section>

        <nav className="sticky bottom-4 z-10 mb-2">
          <div className="flex gap-2 overflow-x-auto py-2 px-3 rounded-xl mx-auto w-fit max-w-full" style={{ border: "1px solid var(--border)", backgroundColor: "color-mix(in srgb, var(--bg-primary) 90%, transparent)", backdropFilter: "blur(12px)" }}>
            {SECTIONS.map(([id, label]) => (
              <a
                key={id}
                href={`#${id}`}
                className="font-mono text-[11px] px-2.5 py-1 rounded-lg whitespace-nowrap transition-all"
                style={{ color: "var(--text-secondary)" }}
                onMouseEnter={(e) => { e.currentTarget.style.color = "var(--accent)" }}
                onMouseLeave={(e) => { e.currentTarget.style.color = "var(--text-secondary)" }}
              >
                {label}
              </a>
            ))}
          </div>
        </nav>

        <p className="font-mono text-[11px]" style={{ color: "var(--text-muted)" }}>
          Owner-Code: <span style={{ color: "var(--text-secondary)" }}>/fleet owner-code</span> in der Server-Konsole erzeugen, dann hier über Login einlösen<span className="fleet-blink" style={{ color: "var(--accent)" }}>▌</span>
        </p>
      </div>
      <Footer className="px-6 pb-6" />
    </div>
  )
}
