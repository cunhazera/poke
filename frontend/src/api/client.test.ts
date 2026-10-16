import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { sessionStore } from '../auth/session'
import { fake, failWith, server, USERS, tokenOf } from '../test/fakeApi'
import { ApiError, NETWORK_ERROR_STATUS, get, request } from './client'

function signInAsOak() {
  const { password: _, ...user } = USERS.oak
  sessionStore.signIn(tokenOf('oak'), 3600, user)
}

describe('request', () => {
  it('sends the session token and JSON, and returns the status with the parsed body', async () => {
    signInAsOak()

    const { status, data } = await request<{ pokedexNumber: number }>('/local-pokemon/sync/4', { method: 'POST' })

    expect(status).toBe(201)
    expect(data.pokedexNumber).toBe(4)
    expect(fake.requests.at(-1)).toMatchObject({ method: 'POST', path: '/local-pokemon/sync/4', authorization: 'Bearer token-oak' })
  })

  it('sends no Authorization header when signed out', async () => {
    await get('/pokemon/1')

    expect(fake.requests.at(-1)?.authorization).toBeNull()
  })

  it('turns a problem response into an ApiError with the status, message and field errors', async () => {
    failWith('put', '/local-pokemon/1', 400, 'Missing fields', { errors: { 'stats.attack': 'must not be null' } })
    failWith('post', '/local-pokemon', 400, 'Height must have at most one decimal', { field: 'heightM' })

    const missing = await request('/local-pokemon/1', { method: 'PUT', body: {} }).catch((e: unknown) => e)
    const rule = await request('/local-pokemon', { method: 'POST', body: {} }).catch((e: unknown) => e)

    expect(missing).toBeInstanceOf(ApiError)
    expect(missing).toMatchObject({ status: 400, message: 'Missing fields', fieldErrors: { 'stats.attack': 'must not be null' } })
    expect(rule).toMatchObject({ fieldErrors: { heightM: 'Height must have at most one decimal' } })
  })

  it('signs out when a call made with the session token gets a 401 (expired or revoked token)', async () => {
    signInAsOak()
    failWith('get', '/local-pokemon/1', 401, 'Jwt expired')

    await expect(get('/local-pokemon/1')).rejects.toMatchObject({ status: 401 })

    expect(sessionStore.get()).toBeNull()
  })

  it('keeps the session when a call fails with 403 (signed in, but not allowed)', async () => {
    const { password: _, ...user } = USERS.ash
    sessionStore.signIn(tokenOf('ash'), 3600, user)

    await expect(request('/local-pokemon/1', { method: 'DELETE' })).rejects.toMatchObject({ status: 403 })

    expect(sessionStore.get()?.user.username).toBe('ash')
  })

  it('reports an unreachable server as a network error, not as a crash', async () => {
    server.use(http.get('http://localhost:3000/api/v1/pokemon/1', () => HttpResponse.error()))

    await expect(get('/pokemon/1')).rejects.toMatchObject({ status: NETWORK_ERROR_STATUS, message: expect.stringContaining("Can't reach the server") })
  })

  it('returns no body for a 204', async () => {
    signInAsOak()

    const { status, data } = await request('/local-pokemon/2', { method: 'DELETE' })

    expect(status).toBe(204)
    expect(data).toBeUndefined()
  })
})
