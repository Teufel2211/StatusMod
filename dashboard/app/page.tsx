import Link from "next/link"
import Footer from "@/components/footer"

const features = [
  { icon: "◉", title: "Live Player Sync", text: "Status, Farben und Köpfe in Echtzeit – per SSE direkt ins Panel." },
  { icon: "⬡", title: "Fleet Config", text: "Eine Config für alle Server: URL + Secret zentral verteilen." },
  { icon: "⌨", title: "Owner-Zugang", text: "One-Time-Code aus der Server-Konsole, Sitzung per Cookie." },
]

export default function LandingPage() {
  return (
    <div className="min-h-screen flex flex-col hero-mesh bg-grid" style={{ backgroundColor: "var(--bg-primary)" }}>
      <div className="flex-1 flex flex-col items-center justify-center px-4 pb-16 pt-16">
        <div className="relative text-center max-w-2xl w-full">
          <div className="fade-up inline-flex items-center gap-3 mb-6 px-4 py-1.5 rounded-full font-mono text-xs tracking-widest uppercase" style={{ border: "1px solid var(--accent-border)", backgroundColor: "var(--accent-dim)", color: "var(--accent)" }}>
            <span className="pulse-dot" />
            StatusMod v2 · Ops Console
          </div>

          <h1 className="fade-up anim-d1 font-display font-bold mb-4 leading-[1.02] text-glow" style={{ color: "var(--text-primary)", fontSize: "clamp(2.8rem, 7vw, 4.8rem)" }}>
            Server
            <br />
            <span className="text-gradient">Dashboard</span>
          </h1>

          <p className="fade-up anim-d2 text-lg mb-10 leading-relaxed max-w-lg mx-auto" style={{ color: "var(--text-secondary)" }}>
            Player statuses, fleet config and live ops for your Minecraft servers — all from one console.
          </p>

          <div className="fade-up anim-d3 flex flex-col sm:flex-row gap-3 justify-center mb-14">
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

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 text-left">
            {features.map((f, i) => (
              <div key={f.title} className={`card card-glow fade-up anim-d${i + 3}`}>
                <div className="font-display text-2xl mb-2" style={{ color: "var(--accent)" }}>{f.icon}</div>
                <div className="font-display font-semibold text-sm mb-1" style={{ color: "var(--text-primary)" }}>{f.title}</div>
                <p className="text-xs leading-relaxed" style={{ color: "var(--text-secondary)" }}>{f.text}</p>
              </div>
            ))}
          </div>

          <div className="fade-up anim-d6 font-mono text-xs mt-10" style={{ color: "var(--text-muted)" }}>
            <span style={{ color: "var(--accent)" }}>$</span> statusmod --fleet --sync --live
          </div>
        </div>
      </div>

      <Footer className="pb-8" />
    </div>
  )
}
