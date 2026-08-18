import type { Metadata } from "next"
import "./globals.css"
import { ThemeProvider } from "@/components/theme-provider"

export const metadata: Metadata = {
  title: "StatusMod Dashboard",
  description: "Manage your Minecraft server status and players",
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className="dark" suppressHydrationWarning>
      <body className="min-h-screen bg-grid">
        <ThemeProvider>{children}</ThemeProvider>
      </body>
    </html>
  )
}
