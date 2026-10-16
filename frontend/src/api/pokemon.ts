import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { get, searchParam } from './client'
import type { Page, PokemonDetail, PokemonSummary } from './types'

/** The catalog is PokeAPI's: it practically never changes, so it stays fresh for the whole visit. */
const CATALOG_STALE_TIME = 60 * 60_000

export const pokemonKeys = {
  page: (page: number, size: number, name: string) => ['pokemon', 'page', page, size, name] as const,
  detail: (id: number) => ['pokemon', 'detail', id] as const,
}

/**
 * US01, and the search by name. `page` is zero-based, like the API; the previous page stays on screen while the next
 * one loads. Each search is cached like a page, so going back to it is instant.
 */
export function usePokemonPage(page: number, size: number, name = '') {
  return useQuery({
    queryKey: pokemonKeys.page(page, size, name),
    queryFn: ({ signal }) => get<Page<PokemonSummary>>(`/pokemon?page=${page}&size=${size}${searchParam('name', name)}`, signal),
    placeholderData: keepPreviousData,
    staleTime: CATALOG_STALE_TIME,
  })
}

/** US02. */
export function usePokemonDetail(id: number) {
  return useQuery({
    queryKey: pokemonKeys.detail(id),
    queryFn: ({ signal }) => get<PokemonDetail>(`/pokemon/${id}`, signal),
    staleTime: CATALOG_STALE_TIME,
  })
}
