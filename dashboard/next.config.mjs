import { withSentryConfig } from "@sentry/nextjs"

/** @type {import('next').NextConfig} */
const nextConfig = {
  poweredByHeader: false,
  reactStrictMode: true,
  webpack: (config, { isServer }) => {
    if (isServer) {
      config.externals.push({ "@node-rs/argon2": "commonjs @node-rs/argon2" })
    }
    return config
  },
  async headers() {
    return [
      {
        source: "/(.*)",
        headers: [
          { key: "X-Frame-Options", value: "DENY" },
          { key: "X-Content-Type-Options", value: "nosniff" },
          { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
          { key: "Strict-Transport-Security", value: "max-age=31536000; includeSubDomains; preload" },
          { key: "X-Permitted-Cross-Domain-Policies", value: "none" },
        ],
      },
      {
        source: "/api/(.*)",
        headers: [
          { key: "Content-Security-Policy", value: "default-src 'self'" },
        ],
      },
    ]
  },
}

export default withSentryConfig(nextConfig, {
  silent: !process.env.CI,
  tunnelRoute: "/monitoring",
  hideSourceMaps: true,
  disableLogger: true,
  automaticVercelMonitors: false,
})
