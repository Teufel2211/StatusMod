import Link from "next/link"
import Footer from "@/components/footer"

export default function LandingPage() {
  return (
    <div className="min-h-screen flex flex-col items-center justify-center px-4 pb-16">
      <div className="absolute inset-0 bg-gradient-to-b from-[#d4c892]/5 via-transparent to-transparent pointer-events-none" />

      <div className="relative text-center max-w-lg">
        <div className="inline-flex items-center gap-3 mb-6">
          <div className="w-3 h-3 rounded-full bg-[#d4c892] animate-pulse" />
          <span className="text-xs font-mono text-[#636980] tracking-widest uppercase">StatusMod v2</span>
        </div>

        <h1 className="text-5xl font-display text-[#e8e6e0] mb-4 leading-tight">
          Server{" "}
          <span className="text-gradient">Dashboard</span>
        </h1>

        <p className="text-[#9ea3b3] text-lg mb-10 leading-relaxed">
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
