"use client"

import { useEffect, useState } from "react"
import { usePathname, useRouter } from "next/navigation"
import Sidebar from "@/components/sidebar"
import FleetTopbar from "@/components/fleet-topbar"
import Footer from "@/components/footer"
import { ensureSession } from "@/lib/client/auth"

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter()
  const pathname = usePathname()
  const [authed, setAuthed] = useState(false)
  const [checking, setChecking] = useState(true)
  const isFleet = pathname === "/dashboard/fleet" || pathname.startsWith("/dashboard/fleet/")

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

  if (checking) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: "var(--bg-primary)" }}>
        <div className="flex items-center gap-3">
          <div className="w-2 h-2 rounded-full animate-pulse" style={{ backgroundColor: "var(--accent)" }} />
          <span className="text-sm font-mono" style={{ color: "var(--text-muted)" }}>Loading...</span>
        </div>
      </div>
    )
  }

  if (!authed) return null

  if (isFleet) {
    return (
      <div className="fleet-theme min-h-screen hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)", color: "var(--text-primary)" }}>
        <FleetTopbar />
        <main className="flex-1 overflow-auto flex flex-col">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 py-6 flex-1 w-full">
            {children}
          </div>
          <Footer className="px-6 pb-6" />
        </main>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)" }}>
      <Sidebar />
      <main className="flex-1 overflow-auto flex flex-col">
        <div className="max-w-6xl mx-auto px-8 py-8 flex-1 w-full">
          {children}
        </div>
        <Footer className="px-8 pb-6" />
      </main>
    </div>
  )
}
