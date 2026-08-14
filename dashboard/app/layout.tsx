import type { Metadata } from "next"
import "./globals.css"

export const metadata: Metadata = {
  title: "StatusMod Dashboard",
  description: "Manage your Minecraft server status and players",
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-grid">{children}</body>
    </html>
  )
}
