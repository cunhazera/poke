import { afterEach, describe, expect, it, vi } from 'vitest'
import { sessionStore } from './session'

const ash = { id: 1, username: 'ash', email: 'ash@pallet.town', role: 'USER' as const }

describe('sessionStore', () => {
  afterEach(() => vi.useRealTimers())

  it('survives a reload of the page (persisted in localStorage)', () => {
    sessionStore.signIn('token-ash', 3600, ash)

    sessionStore.reload()

    expect(sessionStore.get()).toMatchObject({ token: 'token-ash', user: ash })
  })

  it('treats an expired token as signed out', () => {
    vi.useFakeTimers()
    sessionStore.signIn('token-ash', 60, ash)

    vi.advanceTimersByTime(60_000)

    expect(sessionStore.get()).toBeNull()
    expect(localStorage.getItem('poke.session')).toBeNull()
  })

  it('ignores a corrupt stored session', () => {
    localStorage.setItem('poke.session', '{not json')

    sessionStore.reload()

    expect(sessionStore.get()).toBeNull()
  })

  it('notifies subscribers on sign-in and sign-out', () => {
    const listener = vi.fn()
    const unsubscribe = sessionStore.subscribe(listener)

    sessionStore.signIn('token-ash', 3600, ash)
    sessionStore.signOut()
    unsubscribe()
    sessionStore.signIn('token-ash', 3600, ash)

    expect(listener).toHaveBeenCalledTimes(2)
  })
})
