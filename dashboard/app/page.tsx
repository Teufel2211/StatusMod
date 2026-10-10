import Link from "next/link"
import Footer from "@/components/footer"

const features = [
  { icon: "◉", title: "Live Ops", text: "Systemstatus, Spieler live im Feed und Aktivitäts-Stream in Echtzeit.", stat: "SSE" },
  { icon: "⬡", title: "Fleet Control", text: "Eine Config für alle Server: URL + Secret zentral verteilen.", stat: "60s" },
  { icon: "⌨", title: "Owner Access", text: "One-Time-Code aus der Konsole, Sitzung per Cookie.", stat: "10m" },
]

const bullets = ["24 Mod-JARs · 4 Loader", "1.21.11 → 26.3", "Bedrock-ready"]

export default function LandingPage() {
  return (
    <div className="min-h-screen flex flex-col hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)" }}>
      <div className="flex-1 flex flex-col items-center justify-center px-4 pb-16 pt-20">
        <div className="relative text-center max-w-3xl w-full">
          <div className="fade-up inline-flex items-center gap-2.5 mb-7 px-4 py-1.5 rounded-full font-mono text-xs tracking-widest uppercase" style={{ border: "1px solid var(--accent-border)", backgroundColor: "var(--accent-dim)", color: "var(--accent)" }}>
            <span className="pulse-dot" />
            StatusMod · Server Ops
          </div>

          <h1 className="fade-up anim-d1 font-display font-bold leading-[0.98] text-glow mb-5" style={{ color: "var(--text-primary)", fontSize: "clamp(3rem, 8vw, 5.5rem)" }}>
            Command your
            <br />
            <span className="text-gradient">Minecraft fleet.</span>
          </h1>

          <p className="fade-up anim-d2 text-lg mb-4 leading-relaxed max-w-xl mx-auto" style={{ color: "var(--text-secondary)" }}>
            Player statuses, live ops and fleet config for all your servers — one console, zero file edits.
          </p>

          <div className="fade-up anim-d2 flex flex-wrap justify-center gap-2 mb-10">
            {bullets.map((b) => (
              <span key={b} className="font-mono text-[11px] px-3 py-1 rounded-full" style={{ border: "1px solid var(--border)", color: "var(--text-muted)", backgroundColor: "var(--bg-card)" }}>
                {b}
              </span>
            ))}
          </div>

          <div className="fade-up anim-d3 flex flex-col sm:flex-row gap-3 justify-center mb-16">
            <Link href="/setup" className="btn-primary text-base px-7 py-3">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 6v6m0 0v6m0-6h6m-6 0H6" />
              </svg>
              Setup Server
            </Link>
            <Link href="/login" className="btn-secondary text-base px-7 py-3">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M11 16l-4-4m0 0l4-4m-4 4h14m-5 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h7a3 3 0 013 3v1" />
              </svg>
              Sign In
            </Link>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-left">
            {features.map((f, i) => (
              <div key={f.title} className={`card card-glow fade-up anim-d${i + 3}`}>
                <div className="flex items-start justify-between mb-3">
                  <div className="font-display text-2xl" style={{ color: "var(--accent)" }}>{f.icon}</div>
                  <span className="font-mono text-[11px] px-2 py-0.5 rounded-full" style={{ border: "1px solid var(--accent-border)", color: "var(--accent)", backgroundColor: "var(--accent-dim)" }}>{f.stat}</span>
                </div>
                <div className="font-display font-semibold mb-1" style={{ color: "var(--text-primary)" }}>{f.title}</div>
                <p className="text-xs leading-relaxed" style={{ color: "var(--text-secondary)" }}>{f.text}</p>
              </div>
            ))}
          </div>
        </div>
      </div>

      <Footer className="pb-8" />
    </div>
  )
}
