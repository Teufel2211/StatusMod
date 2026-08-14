import * as Sentry from "@sentry/nextjs"

export const onRequestError = Sentry.captureRequestError

export function register() {
  if (process.env.NEXT_RUNTIME === "nodejs") {
    import("./sentry.server.config")
  }
  if (process.env.NEXT_RUNTIME === "edge") {
    import("./sentry.edge.config")
  }
}
