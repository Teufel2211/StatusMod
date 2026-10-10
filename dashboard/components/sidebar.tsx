"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { logout } from "@/lib/client/auth"
import { useTheme } from "@/components/theme-provider"

const navItems = [
  { href: "/dashboard", label: "Overview", icon: "◆" },
  { href: "/dashboard/players", label: "Players", icon: "◎" },
  { href: "/dashboard/config", label: "Config", icon: "⚙" },
  { href: "/dashboard/fleet", label: "Fleet", icon: "⬡" },
  { href: "/dashboard/keys", label: "API Keys", icon: "⌨" },
  { href: "/dashboard/audit", label: "Audit Log", icon: "◉" },
]

export default function Sidebar() {
  const pathname = usePathname()
  const router = useRouter()
  const { theme, toggle } = useTheme()

  async function handleLogout() {
    await logout()
    router.push("/")
  }

  return (
    <aside className="w-60 min-h-screen border-r flex flex-col shrink-0" style={{ borderColor: "var(--border)", backgroundColor: "var(--bg-card)" }}>
      <div className="px-5 py-6 border-b" style={{ borderColor: "var(--border)" }}>
        <Link href="/dashboard" className="flex items-center gap-3">
          <div className="relative w-9 h-9 rounded-lg flex items-center justify-center font-display font-bold text-sm" style={{ background: "linear-gradient(135deg, var(--accent), var(--accent-hover))", color: "#0a0a0a", boxShadow: "0 0 20px var(--glow-accent)" }}>
            S
            <span className="absolute -top-1 -right-1 w-2.5 h-2.5 rounded-full pulse-dot" />
          </div>
          <div>
            <div className="font-display font-bold leading-none" style={{ color: "var(--text-primary)" }}>STATUSMOD</div>
            <div className="font-mono text-[10px] tracking-widest mt-1" style={{ color: "var(--text-muted)" }}>OPS CONSOLE</div>
          </div>
        </Link>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-1">
        <div className="kicker px-3 pb-2">Control</div>
        {navItems.map((item) => {
          const active = pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href))
          return (
            <Link
              key={item.href}
              href={item.href}
              className="relative flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm transition-all font-medium"
              style={active ? {
                backgroundColor: "var(--accent-dim)",
                color: "var(--accent)",
                border: "1px solid var(--accent-border)",
                boxShadow: "0 0 16px -4px var(--glow-accent)",
              } : {
                color: "var(--text-muted)",
                border: "1px solid transparent",
              }}
              onMouseEnter={(e) => {
                if (!active) {
                  e.currentTarget.style.color = "var(--text-primary)"
                  e.currentTarget.style.backgroundColor = "var(--bg-hover)"
                }
              }}
              onMouseLeave={(e) => {
                if (!active) {
                  e.currentTarget.style.color = "var(--text-muted)"
                  e.currentTarget.style.backgroundColor = ""
                }
              }}
            >
              {active && (
                <span className="absolute left-0 top-1/2 -translate-y-1/2 w-1 h-6 rounded-full" style={{ backgroundColor: "var(--accent)", boxShadow: "0 0 8px var(--glow-accent)" }} />
              )}
              <span className="w-5 text-center text-base">{item.icon}</span>
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div className="px-3 py-4 border-t space-y-1" style={{ borderColor: "var(--border)" }}>
        <div className="kicker px-3 pb-2">Session</div>
        <button
          onClick={toggle}
          className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm w-full transition-all"
          style={{ color: "var(--text-muted)" }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = "var(--text-secondary)"
            e.currentTarget.style.backgroundColor = "var(--bg-hover)"
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = "var(--text-muted)"
            e.currentTarget.style.backgroundColor = ""
          }}
        >
          <span className="w-5 text-center">{theme === "dark" ? "☀" : "☾"}</span>
          {theme === "dark" ? "Light Mode" : "Dark Mode"}
        </button>
        <button
          onClick={handleLogout}
          className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm w-full transition-all"
          style={{ color: "var(--text-muted)" }}
          onMouseEnter={(e) => {
            e.currentTarget.style.color = "#ef4444"
            e.currentTarget.style.backgroundColor = "rgba(239,68,68,0.08)"
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.color = "var(--text-muted)"
            e.currentTarget.style.backgroundColor = ""
          }}
        >
          <span className="w-5 text-center">✕</span>
          Sign Out
        </button>
      </div>
    </aside>
  )
}
