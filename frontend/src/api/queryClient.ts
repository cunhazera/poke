import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './client'

/** Retries only what a retry can fix: network errors and 5xx (PokeAPI hiccups), never a 4xx. */
export function shouldRetry(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status >= 400 && error.status < 500) return false
  return failureCount < 2
}

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: shouldRetry,
        staleTime: 60_000,
        refetchOnWindowFocus: false,
      },
    },
  })
}
