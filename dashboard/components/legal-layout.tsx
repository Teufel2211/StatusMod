import Link from "next/link"
import type { ReactNode } from "react"
import Footer from "@/components/footer"

export default function LegalLayout({
  title,
  updated,
  children,
}: {
  title: string
  updated: string
  children: ReactNode
}) {
  return (
    <div className="min-h-screen flex flex-col px-4 py-12">
      <div className="w-full max-w-3xl mx-auto flex-1">
        <Link href="/" className="text-xs font-mono text-[#636980] hover:text-[#9ea3b3] transition-colors">
          ← Zurück zur Startseite
        </Link>

        <div className="mt-6 mb-8">
          <h1 className="text-3xl font-display text-[#e8e6e0] mb-2">{title}</h1>
          <p className="text-xs font-mono text-[#636980]">Zuletzt aktualisiert: {updated}</p>
        </div>

        <div className="card prose prose-invert prose-sm max-w-none prose-headings:font-display prose-headings:text-[#e8e6e0] prose-p:text-[#9ea3b3] prose-li:text-[#9ea3b3] prose-a:text-[#d4c892] prose-strong:text-[#e8e6e0]">
          {children}
        </div>
      </div>

      <Footer className="mt-12" />
    </div>
  )
}
