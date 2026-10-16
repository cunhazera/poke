import styles from './Pagination.module.css'

interface Props {
  /** zero-based */
  page: number
  totalPages: number
  hasPrevious: boolean
  hasNext: boolean
  onChange: (page: number) => void
  busy?: boolean
}

export function Pagination({ page, totalPages, hasPrevious, hasNext, onChange, busy }: Props) {
  return (
    <nav aria-label="Pagination" className={styles.pagination}>
      <button type="button" className="button" disabled={!hasPrevious} onClick={() => onChange(page - 1)} aria-label="Previous page">
        ← Previous
      </button>
      <span className={styles.position} aria-live="polite" aria-busy={busy}>
        Page {page + 1} of {Math.max(totalPages, 1)}
      </span>
      <button type="button" className="button" disabled={!hasNext} onClick={() => onChange(page + 1)} aria-label="Next page">
        Next →
      </button>
    </nav>
  )
}
