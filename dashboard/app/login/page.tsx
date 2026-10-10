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
      router.push(ownerMode ? "/admin-dashboard" : "/dashboard")
    } catch {
      setError("Connection failed")
    } finally {
      setLoading(false)
    }
  }

  const codeLength = ownerMode ? 16 : 8

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)" }}>
      <div className="w-full max-w-sm fade-up">
        <div className="term-window">
          <div className="term-bar">
            <span className="term-dot" style={{ backgroundColor: "#ef4444" }} />
            <span className="term-dot" style={{ backgroundColor: "#f59e0b" }} />
            <span className="term-dot" style={{ backgroundColor: "#10b981" }} />
            <span className="ml-2">statusmod — auth</span>
          </div>
          <div className="p-6 sm:p-8">
            <div className="kicker mb-2">{ownerMode ? "Owner access" : "Player access"}</div>
            <h1 className="font-display font-bold text-3xl mb-2 text-glow" style={{ color: "var(--text-primary)" }}>
              {ownerMode ? (<>Owner <span className="text-gradient">Login</span></>) : (<>Sign <span className="text-gradient">In</span></>)}
            </h1>
            <p className="text-sm mb-6" style={{ color: "var(--text-muted)" }}>
              {ownerMode
                ? "Use /fleet owner-code on the server console for your 16-character owner code"
                : "Use /code in-game to get your 8-character login code"}
            </p>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="label font-mono text-xs uppercase tracking-widest">{ownerMode ? "Owner Code" : "Login Code"}</label>
                <input
                  className="input tracking-widest text-center text-lg uppercase"
                  placeholder={ownerMode ? "AAAAAAAAAAAAAAAA" : "A3kR9xZ2"}
                  value={code}
                  onChange={(e) => setCode(e.target.value.toUpperCase())}
                  maxLength={codeLength}
                  autoFocus
                />
              </div>

              {error && (
                <div className="text-sm rounded-xl px-3 py-2 font-mono" style={{ color: "#ef4444", backgroundColor: "rgba(239,68,68,0.1)", border: "1px solid rgba(239,68,68,0.25)" }}>
                  [!] {error}
                </div>
              )}

              <button type="submit" className="btn-primary w-full" disabled={code.length !== codeLength || loading}>
                {loading ? "Verifying..." : ownerMode ? "Sign In as Owner" : "Sign In"}
              </button>

              <button
                type="button"
                className="w-full text-xs underline underline-offset-2 font-mono"
                style={{ color: "var(--text-muted)" }}
                onClick={() => { setOwnerMode(!ownerMode); setCode(""); setError("") }}
              >
                {ownerMode ? "> Use player login code instead" : "> Log in as owner with one-time code"}
              </button>
            </form>

            <p className="text-center mt-6 text-xs font-mono" style={{ color: "var(--text-muted)" }}>
              Code expires in 10 minutes &mdash; {ownerMode ? "run /fleet owner-code again if needed" : "use /code again if needed"}
            </p>
          </div>
        </div>
      </div>

      <Footer className="mt-12" />
    </div>
  )
}
