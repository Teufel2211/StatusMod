import Link from "next/link"
import Footer from "@/components/footer"

export default function LandingPage() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 pb-16" style={{ backgroundColor: "var(--bg-primary)" }}>
      <div className="absolute inset-0 pointer-events-none" style={{ background: "linear-gradient(to bottom, var(--accent), transparent 40%)", opacity: 0.05 }} />

      <div className="relative text-center max-w-lg">
        <div className="inline-flex items-center gap-3 mb-6">
          <div className="w-3 h-3 rounded-full animate-pulse" style={{ backgroundColor: "var(--accent)" }} />
          <span className="text-xs font-mono tracking-widest uppercase" style={{ color: "var(--text-muted)" }}>StatusMod v2</span>
        </div>

        <h1 className="text-5xl font-display mb-4 leading-tight" style={{ color: "var(--text-primary)" }}>
          Server{" "}
          <span className="text-gradient">Dashboard</span>
        </h1>

        <p className="text-lg mb-10 leading-relaxed" style={{ color: "var(--text-secondary)" }}>
          Manage player statuses, customize presets, and run your Minecraft server
          — all from one place.
        </p>

        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <Link href="/setup" className="btn-primary">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
            </svg>
            Setup Server
          </Link>
          <Link href="/login" className="btn-secondary">
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
            </svg>
            Sign In
          </Link>
        </div>
      </div>

      <Footer className="mt-10" />
    </div>
  )
}
