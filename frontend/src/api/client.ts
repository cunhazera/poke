import { sessionStore } from '../auth/session'
import type { ProblemDetail } from './types'

/** A failed API call, carrying the server's problem details so forms can point at the offending field. */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed (${status})`)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  /** Field paths (`stats.hp`, `types[1]`) mapped to their messages: the `errors` map plus the single `field`. */
  get fieldErrors(): Record<string, string> {
    const errors = { ...this.problem.errors }
    if (this.problem.field) errors[this.problem.field] = this.message
    return errors
  }
}

export const NETWORK_ERROR_STATUS = 0

export const isNotFound = (error: unknown) => error instanceof ApiError && error.status === 404

export interface ApiResponse<T> {
  status: number
  data: T
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  signal?: AbortSignal
  /** a token to use instead of the session's (right after login, before the session exists) */
  token?: string
}

/**
 * Calls the API on this origin. Sends the bearer token when signed in, sends and reads JSON, and turns every failure
 * into an {@link ApiError}. A 401 on a call that carried a token means the token is no longer good: sign out.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const { method = 'GET', body, signal } = options
  const token = options.token ?? sessionStore.get()?.token
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(new URL(`/api/v1${path}`, window.location.origin), {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new ApiError(NETWORK_ERROR_STATUS, { title: 'Network error', detail: "Can't reach the server. Check your connection and try again." })
  }

  const payload = await readJson(response)
  if (!response.ok) {
    if (response.status === 401 && token && !options.token) sessionStore.signOut()
    throw new ApiError(response.status, (payload as ProblemDetail | undefined) ?? { title: response.statusText })
  }
  return { status: response.status, data: payload as T }
}

/** `&name=…` for a search, or nothing when there is none. */
export function searchParam(key: string, text: string) {
  return text ? `&${key}=${encodeURIComponent(text)}` : ''
}

export async function get<T>(path: string, signal?: AbortSignal): Promise<T> {
  return (await request<T>(path, { signal })).data
}

async function readJson(response: Response): Promise<unknown> {
  const text = await response.text()
  if (!text) return undefined
  try {
    return JSON.parse(text)
  } catch {
    return undefined
  }
}
