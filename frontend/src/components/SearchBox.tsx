import { type FormEvent, useCallback, useEffect, useState } from 'react'
import styles from './SearchBox.module.css'

const DEBOUNCE_MS = 300
export const MAX_SEARCH_LENGTH = 50

interface Props {
  label: string
  placeholder: string
  /** the search currently applied (from the URL) */
  value: string
  /** applies a search, after the user pauses typing; '' clears it */
  onSearch: (text: string) => void
  /** Enter pressed: return true when handled (e.g. a number jumps to that Pokémon) */
  onSubmit?: (text: string) => boolean
}

/** A search field that applies what was typed once the user pauses, and follows the URL (back, links, clear). */
export function SearchBox({ label, placeholder, value, onSearch, onSubmit }: Props) {
  const [draft, setDraft] = useState(value)
  const [applied, setApplied] = useState(value)
  /** the last search this box applied itself, so its own URL update never overwrites newer typing */
  const [sent, setSent] = useState<string | null>(null)

  // The URL changed from outside (back button, a link, Clear search): show it in the box.
  if (value !== applied) {
    setApplied(value)
    if (value === sent) setSent(null)
    else if (draft.trim() !== value) setDraft(value)
  }

  const apply = useCallback((text: string) => {
    setSent(text)
    onSearch(text)
  }, [onSearch])

  useEffect(() => {
    const text = draft.trim()
    if (text === value) return
    const timer = setTimeout(() => apply(text), DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [draft, value, apply])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const text = draft.trim()
    if (onSubmit?.(text)) return
    if (text !== value) apply(text)
  }

  return (
    <form role="search" className={styles.search} onSubmit={submit}>
      <label className="visually-hidden" htmlFor="search-box">{label}</label>
      <span className={styles.icon} aria-hidden="true">⌕</span>
      <input
        id="search-box"
        type="search"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        placeholder={placeholder}
        maxLength={MAX_SEARCH_LENGTH}
        autoComplete="off"
        spellCheck={false}
        enterKeyHint="search"
      />
    </form>
  )
}
