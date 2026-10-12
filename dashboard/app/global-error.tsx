"use client"

import * as Sentry from "@sentry/nextjs"
import { useEffect } from "react"

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  useEffect(() => {
    Sentry.captureException(error)
  }, [error])

  return (
    <html lang="de">
      <body style={{ background: "#0b0f14", color: "#e6edf3", fontFamily: "sans-serif" }}>
        <div style={{ minHeight: "100vh", display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", gap: 16 }}>
          <h2>Etwas ist schiefgelaufen.</h2>
          <button
            onClick={() => reset()}
            style={{ padding: "8px 20px", borderRadius: 8, border: "1px solid #2dd4bf", background: "transparent", color: "#2dd4bf", cursor: "pointer" }}
          >
            Erneut versuchen
          </button>
        </div>
      </body>
    </html>
  )
}
