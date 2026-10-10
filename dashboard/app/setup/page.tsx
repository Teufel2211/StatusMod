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
      <div className="min-h-screen flex flex-col items-center justify-center px-4 hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)" }}>
        <div className="w-full max-w-lg fade-up">
          <div className="term-window">
            <div className="term-bar">
              <span className="term-dot" style={{ backgroundColor: "#ef4444" }} />
              <span className="term-dot" style={{ backgroundColor: "#f59e0b" }} />
              <span className="term-dot" style={{ backgroundColor: "#10b981" }} />
              <span className="ml-2">statusmod — claim: OK</span>
            </div>
            <div className="p-6 sm:p-8">
              <div className="kicker mb-2">Server linked</div>
              <h1 className="font-display font-bold text-3xl mb-2" style={{ color: "var(--text-primary)" }}>
                Server <span className="text-gradient">Claimed</span>
              </h1>
              <p className="text-sm mb-6" style={{ color: "var(--text-muted)" }}>Your server is registered. The API key is delivered automatically.</p>

              {result.api_key && (
                <div className="rounded-lg p-4 mb-4" style={{ border: "1px solid var(--accent-border)", backgroundColor: "var(--accent-dim)" }}>
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

              <div className="rounded-lg p-4 text-xs space-y-1.5 mb-6 font-mono" style={{ color: "var(--text-muted)", border: "1px solid var(--border)" }}>
                <div><span style={{ color: "var(--accent)" }}>$</span> claim complete — key auto-delivered (≤ 30s)</div>
                <div><span style={{ color: "var(--accent)" }}>$</span> fallback: key into <span style={{ color: "var(--text-secondary)" }}>config/statusmod/config.json</span></div>
                <div><span style={{ color: "var(--accent)" }}>$</span> <span style={{ color: "var(--text-secondary)" }}>/code</span> works in-game, no restart</div>
              </div>

              <button className="btn-primary w-full" onClick={goToDashboard}>
                Go to Dashboard
              </button>
            </div>
          </div>
        </div>

        <Footer className="mt-12" />
      </div>
    )
  }

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)" }}>
      <div className="w-full max-w-sm fade-up">
        <div className="term-window">
          <div className="term-bar">
            <span className="term-dot" style={{ backgroundColor: "#ef4444" }} />
            <span className="term-dot" style={{ backgroundColor: "#f59e0b" }} />
            <span className="term-dot" style={{ backgroundColor: "#10b981" }} />
            <span className="ml-2">statusmod — claim</span>
          </div>
          <div className="p-6 sm:p-8">
            <div className="kicker mb-2">New server</div>
            <h1 className="font-display font-bold text-3xl mb-2" style={{ color: "var(--text-primary)" }}>
              Server <span className="text-gradient">Setup</span>
            </h1>
            <p className="text-sm mb-6" style={{ color: "var(--text-muted)" }}>
              Enter the 16-character setup code from your server console
            </p>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="label font-mono text-xs uppercase tracking-widest">Setup Code</label>
                <input
                  className="input tracking-widest text-center text-lg uppercase"
                  placeholder="Xk9mR2pL7vN3bW8z"
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  maxLength={16}
                  autoFocus
                />
              </div>

              {error && (
                <div className="text-sm rounded-lg px-3 py-2 font-mono" style={{ color: "#ef4444", backgroundColor: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.25)" }}>
                  [!] {error}
                </div>
              )}

              <button type="submit" className="btn-primary w-full" disabled={code.length !== 16 || loading}>
                {loading ? "Verifying..." : "Claim Server"}
              </button>
            </form>

            <p className="text-center mt-6 text-xs font-mono" style={{ color: "var(--text-muted)" }}>
              Code appears only in the server console log.
              <br />
              <span style={{ color: "var(--accent)", opacity: 0.7 }}>Expires in 24 hours</span>
            </p>
          </div>
        </div>
      </div>

      <Footer className="mt-12" />
    </div>
  )
}
