import { unauthorized } from "@/lib/response"
import { getServiceClient } from "@/lib/supabase"
import { requireSession } from "@/lib/auth"

export const dynamic = "force-dynamic"
export const runtime = "nodejs"

export async function GET(request: Request) {
  const session = requireSession(request)
  if (!session) return unauthorized()

  const sb = getServiceClient()

  const encoder = new TextEncoder()
  const stream = new ReadableStream({
    start(controller) {
      let closed = false

      const send = (event: string, data: unknown) => {
        if (closed) return
        try {
          controller.enqueue(encoder.encode(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`))
        } catch { /* ignore */ }
      }

      send("connected", { server_id: session.server_id })

      const interval = setInterval(async () => {
        if (closed) { clearInterval(interval); return }
        try {
          const { data } = await (sb as any)
            .from("players")
            .select("uuid, username, status, color, updated_at")
            .eq("server_id", session.server_id)
            .order("updated_at", { ascending: false })
            .limit(10)

          if (data && !closed) {
            send("players", data ?? [])
          }
        } catch { /* silent */ }
      }, 30_000)

      request.signal.addEventListener("abort", () => {
        closed = true
        clearInterval(interval)
        controller.close()
      })
    },
  })

  return new Response(stream, {
    headers: {
      "Content-Type": "text/event-stream",
      "Cache-Control": "no-cache, no-transform",
      Connection: "keep-alive",
      "X-Accel-Buffering": "no",
    },
  })
}
