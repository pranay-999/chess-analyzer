// Live updates from the server over a WebSocket (STOMP protocol, see WebSocketConfig.java).
//
// One connection is shared by the whole app. Components call useTopic(...) to listen
// to a destination like "/topic/games/k3f9x2qp"; the hook subscribes, and unsubscribes
// when the component goes away. If the connection drops, @stomp/stompjs reconnects
// and we subscribe again.

import { Client, type StompSubscription } from '@stomp/stompjs'
import { useEffect, useRef } from 'react'

type Listener = { topic: string; onMessage: (body: unknown) => void; onConnect?: () => void; sub?: StompSubscription }

const listeners = new Set<Listener>()

const client = new Client({
  // Same host as the page; Vite's dev server forwards /ws to Spring (vite.config.ts).
  brokerURL: `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws`,
  reconnectDelay: 2000,
  onConnect: () => {
    for (const l of listeners) {
      subscribe(l)
      l.onConnect?.() // we may have missed messages while disconnected: let the page re-fetch
    }
  },
})

function subscribe(l: Listener) {
  l.sub = client.subscribe(l.topic, (message) => l.onMessage(JSON.parse(message.body)))
}

/** Calls onMessage with every message published to `topic` (null = don't listen). */
export function useTopic<T>(topic: string | null, onMessage: (body: T) => void, onConnect?: () => void) {
  // Keep the latest callbacks in a ref, so re-renders don't resubscribe.
  const callbacks = useRef({ onMessage, onConnect })
  useEffect(() => {
    callbacks.current = { onMessage, onConnect }
  })

  useEffect(() => {
    if (!topic) return
    const listener: Listener = {
      topic,
      onMessage: (body) => callbacks.current.onMessage(body as T),
      onConnect: () => callbacks.current.onConnect?.(),
    }
    listeners.add(listener)
    if (client.connected) subscribe(listener)
    if (!client.active) client.activate()
    return () => {
      listeners.delete(listener)
      if (client.connected) listener.sub?.unsubscribe()
      if (listeners.size === 0) void client.deactivate() // nobody listening (e.g. left the online pages)
    }
  }, [topic])
}
