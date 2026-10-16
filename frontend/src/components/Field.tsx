import { type ReactElement, cloneElement, useId } from 'react'
import styles from './Field.module.css'

interface Props {
  label: string
  error?: string
  hint?: string
  /** the input, select or textarea; the label, error and accessibility attributes are wired to it */
  children: ReactElement<{ id?: string; 'aria-invalid'?: boolean; 'aria-describedby'?: string }>
  className?: string
}

/** A labelled control whose error (from the server or a client check) is announced as its description. */
export function Field({ label, error, hint, children, className }: Props) {
  const id = useId()
  const describedBy = error ? `${id}-error` : hint ? `${id}-hint` : undefined
  return (
    <div className={`${styles.field} ${className ?? ''}`}>
      <label htmlFor={id}>{label}</label>
      {cloneElement(children, { id, 'aria-invalid': error ? true : undefined, 'aria-describedby': describedBy })}
      {error ? (
        <span id={`${id}-error`} className={styles.error}>{error}</span>
      ) : hint ? (
        <span id={`${id}-hint`} className={styles.hint}>{hint}</span>
      ) : null}
    </div>
  )
}
