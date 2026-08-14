"use client"

import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"
import { logout } from "@/lib/client/auth"

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

  async function handleLogout() {
    await logout()
    router.push("/")
  }

  return (
    <aside className="w-56 min-h-screen border-r border-[#2d3242] bg-[#0f1117] flex flex-col">
      <div className="px-5 py-6 border-b border-[#2d3242]">
        <Link href="/dashboard" className="flex items-center gap-2.5">
          <div className="w-7 h-7 rounded-lg bg-[#d4c892]/10 border border-[#d4c892]/20 flex items-center justify-center">
            <span className="text-xs text-[#d4c892] font-bold">S</span>
          </div>
          <span className="text-sm font-semibold text-[#e8e6e0]">StatusMod</span>
        </Link>
      </div>

      <nav className="flex-1 px-3 py-4 space-y-1">
        {navItems.map((item) => {
          const active = pathname === item.href || (item.href !== "/dashboard" && pathname.startsWith(item.href))
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-all ${
                active
                  ? "bg-[#d4c892]/10 text-[#d4c892] border border-[#d4c892]/15"
                  : "text-[#636980] hover:text-[#9ea3b3] hover:bg-[#1a1d26]"
              }`}
            >
              <span className="w-5 text-center text-base">{item.icon}</span>
              {item.label}
            </Link>
          )
        })}
      </nav>

      <div className="px-3 py-4 border-t border-[#2d3242]">
        <button
          onClick={handleLogout}
          className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-[#636980] hover:text-red-400 hover:bg-red-900/10 w-full transition-all"
        >
          <span className="w-5 text-center">✕</span>
          Sign Out
        </button>
      </div>
    </aside>
  )
}
