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
      <div className="min-h-screen flex flex-col items-center justify-center px-4">
        <div className="w-full max-w-lg">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-display text-[#e8e6e0] mb-2">
              Server <span className="text-gradient">Claimed</span>
            </h1>
            <p className="text-sm text-[#636980]">Your server is registered. The API key is delivered automatically.</p>
          </div>

          {result.api_key && (
            <div className="card border-[#d4c892]/30 bg-[#d4c892]/5 mb-4">
              <div className="text-sm font-medium text-[#d4c892] mb-2">Mod API Key — auto-delivered to your server</div>
              <code className="block text-xs bg-[#0f1117] rounded-lg px-4 py-3 font-mono text-[#e8e6e0] break-all border border-[#2d3242]">
                {result.api_key}
              </code>
              <div className="mt-3 flex items-center gap-2">
                <button className="btn-primary text-xs px-4 py-2" onClick={copyKey}>
                  {copied ? "Copied!" : "Copy Key"}
                </button>
                <span className="text-xs text-[#636980]">Fallback only — the mod picks it up automatically within 30s</span>
              </div>
            </div>
          )}

          <div className="card text-xs text-[#636980] space-y-1.5 mb-6">
            <div>1. The server fetches the API key automatically (≤ 30s). No config.json edit needed.</div>
            <div>
              2. If that fails, use the key above in <code className="font-mono text-[#9ea3b3]">config/statusmod/config.json</code>.
            </div>
            <div>
              3. <code className="font-mono text-[#9ea3b3]">/code</code> then works in-game — no restart required.
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
    <div className="min-h-screen flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <h1 className="text-3xl font-display text-[#e8e6e0] mb-2">
            Server <span className="text-gradient">Setup</span>
          </h1>
          <p className="text-sm text-[#636980]">
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
            <div className="text-sm text-red-400 bg-red-900/20 rounded-lg px-3 py-2 border border-red-800/30">
              {error}
            </div>
          )}

          <button type="submit" className="btn-primary w-full" disabled={code.length !== 16 || loading}>
            {loading ? "Verifying..." : "Claim Server"}
          </button>
        </form>

        <p className="text-center mt-6 text-xs text-[#636980]">
          Code appears only in the server console log.
          <br />
          <span className="text-[#d4c892]/50">Expires in 24 hours</span>
        </p>
      </div>

      <Footer className="mt-12" />
    </div>
  )
}
