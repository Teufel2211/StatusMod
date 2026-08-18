"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { logout } from "@/lib/client/auth"
import { useTheme } from "@/components/theme-provider"

const navItems = [
  { href: "/dashboard", label: "Overview", icon: "◆" },
  { href: "/dashboard/players", label: "Players", icon: "◎" },
  { href: "/dashboard/config", label: "Config", icon: "⚙" },
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
    <aside className="w-56 min-h-screen border-r flex flex-col" style={{ borderColor: "var(--border)", backgroundColor: "var(--bg-primary)" }}>
      <div className="px-5 py-6 border-b" style={{ borderColor: "var(--border)" }}>
        <Link href="/dashboard" className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-lg flex items-center justify-center" style={{ backgroundColor: "var(--accent-dim)", border: "1px solid var(--accent-border)" }}>
            <span className="text-xs font-bold" style={{ color: "var(--accent)" }}>S</span>
          </div>
          <span className="text-sm font-semibold" style={{ color: "var(--text-primary)" }}>StatusMod</span>
        </Link>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-1">
        {navItems.map((item) => {
          const active = pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href))
          return (
            <Link
              key={item.href}
              href={item.href}
              className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-all"
              style={active ? {
                backgroundColor: "var(--accent-dim)",
                color: "var(--accent)",
                border: "1px solid var(--accent-border)",
              } : {
                color: "var(--text-muted)",
              }}
              onMouseEnter={(e) => {
                if (!active) {
                  e.currentTarget.style.color = "var(--text-secondary)"
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
              <span className="w-5 text-center text-base">{item.icon}</span>
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div className="px-3 py-4 border-t space-y-1" style={{ borderColor: "var(--border)" }}>
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
