import Link from "next/link"

const links = [
  { href: "/agb", label: "AGB" },
  { href: "/datenschutz", label: "Datenschutz" },
  { href: "/nutzungsbedingungen", label: "Nutzungsbedingungen" },
  { href: "/impressum", label: "Impressum" },
]

export default function Footer({ className = "" }: { className?: string }) {
  return (
    <footer className={`text-xs text-[#636980] font-mono ${className}`}>
      <div className="flex flex-wrap items-center justify-center gap-x-5 gap-y-2">
        <span className="text-[#d4c892]/50">▲</span>
        <span>statusmod-dashboard.vercel.app</span>
        {links.map((link) => (
          <Link key={link.href} href={link.href} className="hover:text-[#9ea3b3] transition-colors">
            {link.label}
          </Link>
        ))}
      </div>
    </footer>
  )
}
