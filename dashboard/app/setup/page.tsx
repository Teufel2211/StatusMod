"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Footer from "@/components/footer"
import { setAccessToken } from "@/lib/client/auth"

type SetupResult = {
  access_token: string
  refresh_token: string
  api_key?: string
  key_prefix?: string
  server_id: string
}

export default function SetupPage() {
  const router = useRouter()
  const [code, setCode] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<SetupResult | null>(null)
  const [copied, setCopied] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")
    setLoading(true)

    try {
      const res = await fetch("/api/auth/setup", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code }),
      })

      if (!res.ok) {
        setError("Invalid or expired setup code")
        return
      }

      const data: SetupResult = await res.json()
      setResult(data)
      setAccessToken(data.access_token)
    } catch {
      setError("Connection failed")
    } finally {
      setLoading(false)
    }
  }

  async function copyKey() {
    if (!result?.api_key) return
    try {
      await navigator.clipboard.writeText(result.api_key)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {}
  }

  function goToDashboard() {
    router.push("/dashboard")
  }

  if (result) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center px-4" style={{ backgroundColor: "var(--bg-primary)" }}>
        <div className="w-full max-w-lg">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-display mb-2" style={{ color: "var(--text-primary)" }}>
              Server <span className="text-gradient">Claimed</span>
            </h1>
            <p className="text-sm" style={{ color: "var(--text-muted)" }}>Your server is registered. The API key is delivered automatically.</p>
          </div>

          {result.api_key && (
            <div className="card mb-4" style={{ borderColor: "var(--accent)", backgroundColor: "var(--accent-dim)" }}>
              <div className="text-sm font-medium mb-2" style={{ color: "var(--accent)" }}>Mod API Key — auto-delivered to your server</div>
              <code className="block text-xs rounded-lg px-4 py-3 font-mono break-all" style={{ backgroundColor: "var(--bg-primary)", border: "1px solid var(--border)", color: "var(--text-primary)" }}>
                {result.api_key}
              </code>
              <div className="mt-3 flex items-center gap-2">
                <button className="btn-primary text-xs px-4 py-2" onClick={copyKey}>
                  {copied ? "Copied!" : "Copy Key"}
                </button>
                <span className="text-xs" style={{ color: "var(--text-muted)" }}>Fallback only — the mod picks it up automatically within 30s</span>
              </div>
            </div>
          )}

          <div className="card text-xs space-y-1.5 mb-6" style={{ color: "var(--text-muted)" }}>
            <div>1. The server fetches the API key automatically (≤ 30s). No config.json edit needed.</div>
            <div>
              2. If that fails, use the key above in <code className="font-mono" style={{ color: "var(--text-secondary)" }}>config/statusmod/config.json</code>.
            </div>
            <div>
              3. <code className="font-mono" style={{ color: "var(--text-secondary)" }}>/code</code> then works in-game — no restart required.
            </div>
          </div>

          <button className="btn-primary w-full" onClick={goToDashboard}>
            Go to Dashboard
          </button>
        </div>

        <Footer className="mt-12" />
      </div>
    )
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4" style={{ backgroundColor: "var(--bg-primary)" }}>
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-display mb-2" style={{ color: "var(--text-primary)" }}>
            Server <span className="text-gradient">Setup</span>
          </h1>
          <p className="text-sm" style={{ color: "var(--text-muted)" }}>
            Enter the 16-character setup code from your server console
          </p>
        </div>

        <form onSubmit={handleSubmit} className="card space-y-4">
          <div>
            <label className="label">Setup Code</label>
            <input
              className="input font-mono tracking-widest text-center text-lg uppercase"
              placeholder="Xk9mR2pL7vN3bW8z"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              maxLength={16}
              autoFocus
            />
          </div>

          {error && (
            <div className="text-sm text-red-400 rounded-lg px-3 py-2" style={{ backgroundColor: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.2)" }}>
              {error}
            </div>
          )}

          <button type="submit" className="btn-primary w-full" disabled={code.length !== 16 || loading}>
            {loading ? "Verifying..." : "Claim Server"}
          </button>
        </form>

        <p className="text-center mt-6 text-xs" style={{ color: "var(--text-muted)" }}>
          Code appears only in the server console log.
          <br />
          <span style={{ color: "var(--accent)", opacity: 0.5 }}>Expires in 24 hours</span>
        </p>
      </div>

      <Footer className="mt-12" />
    </div>
  )
}
