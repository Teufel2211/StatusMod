"use client"

import { useState } from "react"
import { useRouter } from "next/navigation"
import Footer from "@/components/footer"
import { setAccessToken } from "@/lib/client/auth"

export default function LoginPage() {
  const router = useRouter()
  const [code, setCode] = useState("")
  const [error, setError] = useState("")
  const [loading, setLoading] = useState(false)
  const [ownerMode, setOwnerMode] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")
    setLoading(true)

    try {
      const endpoint = ownerMode ? "/api/auth/owner-login" : "/api/auth/code"
      const res = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code }),
      })

      if (!res.ok) {
        setError("Invalid or expired code")
        return
      }

      const data = await res.json()
      setAccessToken(data.access_token)
      router.push("/dashboard")
    } catch {
      setError("Connection failed")
    } finally {
      setLoading(false)
    }
  }

  const codeLength = ownerMode ? 16 : 8

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4" style={{ backgroundColor: "var(--bg-primary)" }}>
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="inline-flex items-center gap-2 mb-4">
            <div className="w-2 h-2 rounded-full" style={{ backgroundColor: "var(--accent)" }} />
            <span className="text-xs font-mono tracking-widest uppercase" style={{ color: "var(--text-muted)" }}>StatusMod</span>
          </div>
          <h1 className="text-3xl font-display mb-2" style={{ color: "var(--text-primary)" }}>
            Sign <span className="text-gradient">In</span>
          </h1>
          <p className="text-sm" style={{ color: "var(--text-muted)" }}>
            {ownerMode
              ? "Use /status owner-code on the server console for your 16-character owner code"
              : "Use /code in-game to get your 8-character login code"}
          </p>
        </div>

        <form onSubmit={handleSubmit} className="card space-y-4">
          <div>
            <label className="label">{ownerMode ? "Owner Code" : "Login Code"}</label>
            <input
              className="input font-mono tracking-widest text-center text-lg uppercase"
              placeholder={ownerMode ? "AAAAAAAAAAAAAAAA" : "A3kR9xZ2"}
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              maxLength={codeLength}
              autoFocus
            />
          </div>

          {error && (
            <div className="text-sm text-red-400 rounded-lg px-3 py-2" style={{ backgroundColor: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.2)" }}>
              {error}
            </div>
          )}

          <button type="submit" className="btn-primary w-full" disabled={code.length !== codeLength || loading}>
            {loading ? "Verifying..." : ownerMode ? "Sign In as Owner" : "Sign In"}
          </button>

          <button
            type="button"
            className="w-full text-xs underline underline-offset-2"
            style={{ color: "var(--text-muted)" }}
            onClick={() => { setOwnerMode(!ownerMode); setCode(""); setError("") }}
          >
            {ownerMode ? "Use player login code instead" : "Log in as owner with one-time code"}
          </button>
        </form>

        <p className="text-center mt-6 text-xs" style={{ color: "var(--text-muted)" }}>
          Code expires in 10 minutes &mdash; {ownerMode ? "run /status owner-code again if needed" : "use /code again if needed"}
        </p>
      </div>

      <Footer className="mt-12" />
    </div>
  )
}
