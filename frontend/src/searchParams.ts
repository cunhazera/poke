import { useCallback } from 'react'
import { useSearchParams } from 'react-router'
import { pageFromSearch } from './format'

/**
 * A list's state in the URL: `?page=` (1-based) and a search parameter (`name` or `q`). A new search starts again
 * from the first page; typing replaces the history entry instead of adding one per pause.
 */
export function useListParams(searchKey: 'name' | 'q') {
  const [params, setParams] = useSearchParams()
  const page = pageFromSearch(params.get('page'))
  const search = params.get(searchKey)?.trim() ?? ''

  const setPage = useCallback((next: number) => setParams((current) => {
    const updated = new URLSearchParams(current)
    if (next === 0) updated.delete('page')
    else updated.set('page', String(next + 1))
    return updated
  }), [setParams])

  const setSearch = useCallback((text: string) => setParams((current) => {
    const updated = new URLSearchParams(current)
    updated.delete('page')
    if (text) updated.set(searchKey, text)
    else updated.delete(searchKey)
    return updated
  }, { replace: true }), [setParams, searchKey])

  return { page, search, setPage, setSearch }
}

/** "3 matches for “char”." */
export function matchesText(total: number, search: string) {
  return `${total} ${total === 1 ? 'match' : 'matches'} for “${search}”.`
}
