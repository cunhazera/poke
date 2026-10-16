import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ApiError, NETWORK_ERROR_STATUS } from '../api/client'
import styles from './Status.module.css'

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className={styles.loading} aria-busy="true">
      <span className={styles.spinner} aria-hidden="true" />
      {label}
    </div>
  )
}

/** A failed load, explained in the user's terms, with a retry when retrying can help. */
export function ErrorPanel({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const retryable = !(error instanceof ApiError) || error.status >= 500 || error.status === NETWORK_ERROR_STATUS
  return (
    <div role="alert" className={`card ${styles.panel}`}>
      <h2>Something went wrong</h2>
      <p>{error instanceof Error ? error.message : 'An unexpected error occurred.'}</p>
      {retryable && onRetry && (
        <button type="button" className="button primary" onClick={onRetry}>Try again</button>
      )}
    </div>
  )
}

export function NotFound({ title = 'Page not found', children }: { title?: string; children?: ReactNode }) {
  return (
    <div className={`card ${styles.panel}`}>
      <h1>{title}</h1>
      {children ?? <p className="muted">There is nothing at this address.</p>}
      <Link to="/" className="button">Back to the Pokédex</Link>
    </div>
  )
}
