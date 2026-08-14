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

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError("")
    setLoading(true)

    try {
      const res = await fetch("/api/auth/code", {
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

  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="text-center mb-8">
          <div className="inline-flex items-center gap-2 mb-4">
            <div className="w-2 h-2 rounded-full bg-[#d4c892]" />
            <span className="text-xs font-mono text-[#636980] tracking-widest uppercase">StatusMod</span>
          </div>
          <h1 className="text-3xl font-display text-[#e8e6e0] mb-2">
            Sign <span className="text-gradient">In</span>
          </h1>
          <p className="text-sm text-[#636980]">
            Use /code in-game to get your 8-character login code
          </p>
        </div>

        <form onSubmit={handleSubmit} className="card space-y-4">
          <div>
            <label className="label">Login Code</label>
            <input
              className="input font-mono tracking-widest text-center text-lg uppercase"
              placeholder="A3kR9xZ2"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              maxLength={8}
              autoFocus
            />
          </div>

          {error && (
            <div className="text-sm text-red-400 bg-red-900/20 rounded-lg px-3 py-2 border border-red-800/30">
              {error}
            </div>
          )}

          <button type="submit" className="btn-primary w-full" disabled={code.length !== 8 || loading}>
            {loading ? "Verifying..." : "Sign In"}
          </button>
        </form>

        <p className="text-center mt-6 text-xs text-[#636980]">
          Code expires in 10 minutes &mdash; use /code again if needed
        </p>
      </div>

      <Footer className="mt-12" />
    </div>
  )
}
