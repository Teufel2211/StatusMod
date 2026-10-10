"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useState } from "react"
import { logout } from "@/lib/client/auth"

function useClock(): string {
  const [now, setNow] = useState("--:--")
  useEffect(() => {
    const tick = () => {
      const d = new Date()
      const p = (n: number) => String(n).padStart(2, "0")
      setNow(`${p(d.getHours())}:${p(d.getMinutes())}`)
    }
    tick()
    const id = setInterval(tick, 15000)
    return () => clearInterval(id)
  }, [])
  return now
}

export default function FleetTopbar() {
  const router = useRouter()
  const clock = useClock()

  async function handleLogout() {
    await logout()
    router.push("/")
  }

  return (
    <header
      className="sticky top-0 z-20 border-b backdrop-blur"
      style={{ borderColor: "var(--border)", backgroundColor: "color-mix(in srgb, var(--bg-primary) 88%, transparent)" }}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 py-3 flex items-center gap-4">
        <div className="flex items-center gap-2.5">
          <div
            className="w-8 h-8 rounded-xl flex items-center justify-center font-display font-bold text-sm"
            style={{ background: "linear-gradient(135deg, var(--accent), var(--cyan))", color: "#04202b", boxShadow: "0 0 18px var(--glow-accent)" }}
          >
            F
          </div>
          <div>
            <div className="font-display font-bold text-sm leading-none" style={{ color: "var(--text-primary)" }}>Fleet Admin</div>
            <div className="text-[10px] tracking-widest mt-0.5 font-mono" style={{ color: "var(--text-muted)" }}>ADMIN-BEREICH</div>
          </div>
        </div>

        <div className="flex-1" />

        <span className="badge-green hidden sm:inline-flex">
          <span className="pulse-dot mr-1.5" />Verbunden
        </span>
        <span className="font-mono text-xs tabular-nums" style={{ color: "var(--text-secondary)" }}>{clock}</span>

        <Link
          href="/dashboard"
          className="text-xs font-medium px-3 py-1.5 rounded-lg transition-all"
          style={{ border: "1px solid var(--border)", color: "var(--text-secondary)" }}
          onMouseEnter={(e) => { e.currentTarget.style.borderColor = "var(--accent-border)"; e.currentTarget.style.color = "var(--text-primary)" }}
          onMouseLeave={(e) => { e.currentTarget.style.borderColor = "var(--border)"; e.currentTarget.style.color = "var(--text-secondary)" }}
        >
          ← Dashboard
        </Link>
        <button
          onClick={handleLogout}
          className="text-xs font-medium px-3 py-1.5 rounded-lg transition-all"
          style={{ border: "1px solid var(--border)", color: "var(--text-muted)" }}
          onMouseEnter={(e) => { e.currentTarget.style.color = "#ef4444"; e.currentTarget.style.borderColor = "rgba(239,68,68,0.4)" }}
          onMouseLeave={(e) => { e.currentTarget.style.color = "var(--text-muted)"; e.currentTarget.style.borderColor = "var(--border)" }}
        >
          Abmelden
        </button>
      </div>
    </header>
  )
}
