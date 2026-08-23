import { NextResponse } from "next/server"
import type { NextRequest } from "next/server"

const CORS_ORIGIN = process.env.CORS_ORIGIN || ""

export function middleware(request: NextRequest) {
  const response = NextResponse.next()

  if (CORS_ORIGIN) {
    const origin = request.headers.get("origin")
    if (origin === CORS_ORIGIN) {
      response.headers.set("Access-Control-Allow-Origin", CORS_ORIGIN)
      response.headers.set("Access-Control-Allow-Credentials", "true")
    }
  }

  if (request.method === "OPTIONS") {
    if (!CORS_ORIGIN) {
      return NextResponse.next()
    }
    return new NextResponse(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": CORS_ORIGIN,
        "Access-Control-Allow-Methods": "GET, POST, PUT, DELETE, OPTIONS",
        "Access-Control-Allow-Headers": "Content-Type, Authorization, x-api-key",
        "Access-Control-Max-Age": "86400",
      },
    })
  }

  return response
}

export const config = {
  matcher: "/api/:path*",
}
