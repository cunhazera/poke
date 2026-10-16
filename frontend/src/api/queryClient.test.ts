import { describe, expect, it } from 'vitest'
import { ApiError, NETWORK_ERROR_STATUS } from './client'
import { shouldRetry } from './queryClient'

describe('shouldRetry', () => {
  it('never retries a client error: the same request would fail the same way', () => {
    for (const status of [400, 401, 403, 404, 409]) {
      expect(shouldRetry(0, new ApiError(status, {}))).toBe(false)
    }
  })

  it('retries an unavailable upstream or a network error, at most twice', () => {
    for (const status of [503, 500, NETWORK_ERROR_STATUS]) {
      expect(shouldRetry(0, new ApiError(status, {}))).toBe(true)
      expect(shouldRetry(1, new ApiError(status, {}))).toBe(true)
      expect(shouldRetry(2, new ApiError(status, {}))).toBe(false)
    }
  })
})
