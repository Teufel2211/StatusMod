"use client"

import { useEffect, useState, useCallback, useRef } from "react"
import { getAccessToken } from "./auth"

type Player = {
  uuid: string
  username: string | null
  status: string | null
  color: string | null
  updated_at: string | null
}

type ConnectionState = "connecting" | "connected" | "disconnected"

export function useRealtimePlayers() {
  const [players, setPlayers] = useState<Player[]>([])
  const [connection, setConnection] = useState<ConnectionState>("disconnected")
  const eventSourceRef = useRef<EventSource | null>(null)
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const mountedRef = useRef(true)

  const connect = useCallback(() => {
    if (!mountedRef.current) return
    if (eventSourceRef.current) {
      eventSourceRef.current.close()
    }

    const token = getAccessToken()
    if (!token) {
      setConnection("disconnected")
      return
    }

    setConnection("connecting")
    const es = new EventSource(`/api/events?token=${encodeURIComponent(token)}`)
    eventSourceRef.current = es

    es.onopen = () => {
      if (mountedRef.current) setConnection("connected")
    }

    es.addEventListener("players", (e) => {
      try {
        const data = JSON.parse(e.data)
        if (mountedRef.current && Array.isArray(data)) {
          setPlayers(data)
        }
      } catch {}
    })

    es.onerror = () => {
      if (mountedRef.current) {
        setConnection("disconnected")
        es.close()
        eventSourceRef.current = null
        reconnectTimerRef.current = setTimeout(connect, 5000)
      }
    }
  }, [])

  useEffect(() => {
    mountedRef.current = true
    connect()
    return () => {
      mountedRef.current = false
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current)
      if (eventSourceRef.current) eventSourceRef.current.close()
    }
  }, [connect])

  return { players, connection }
}
