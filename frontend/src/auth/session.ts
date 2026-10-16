import { useSyncExternalStore } from 'react'
import type { User } from '../api/types'

/** Who is signed in: the bearer token, when it expires, and the user it belongs to. */
export interface Session {
  token: string
  expiresAt: number
  user: User
}

const STORAGE_KEY = 'poke.session'

type Listener = () => void
const listeners = new Set<Listener>()
let current: Session | null = load()

function load(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return null
    const session = JSON.parse(raw) as Session
    return isLive(session) ? session : null
  } catch {
    return null
  }
}

function isLive(session: Session): boolean {
  return typeof session?.token === 'string' && typeof session.expiresAt === 'number' && session.expiresAt > Date.now()
}

function publish(next: Session | null) {
  current = next
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage can be unavailable (private mode, quota); the session then lasts until the tab closes.
  }
  listeners.forEach((listener) => listener())
}

/**
 * The session lives outside React so the API client can read the token and sign the user out on a 401; components
 * subscribe to it through {@link useSession}. An expired session is treated as no session.
 */
export const sessionStore = {
  get(): Session | null {
    if (current && !isLive(current)) publish(null)
    return current
  },
  signIn(token: string, expiresInSeconds: number, user: User) {
    publish({ token, expiresAt: Date.now() + expiresInSeconds * 1000, user })
  },
  signOut() {
    if (current) publish(null)
  },
  subscribe(listener: Listener) {
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  },
  /** Re-reads storage; for tests that seed or clear it. */
  reload() {
    publish(load())
  },
}

export function useSession() {
  const session = useSyncExternalStore(sessionStore.subscribe, sessionStore.get, sessionStore.get)
  return {
    session,
    user: session?.user ?? null,
    isAdmin: session?.user.role === 'ADMIN',
  }
}
