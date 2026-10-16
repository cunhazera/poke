import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { get, request } from './client'
import type { CreateLocalPokemonRequest, LocalPokemon, Page, UpdateLocalPokemonRequest } from './types'

export const localPokemonKeys = {
  all: ['local-pokemon'] as const,
  pages: ['local-pokemon', 'page'] as const,
  page: (page: number, size: number) => ['local-pokemon', 'page', page, size] as const,
  detail: (id: number) => ['local-pokemon', 'detail', id] as const,
}

/** US03: the local store, in Pokédex order (local-only Pokémon last). */
export function useLocalPokemonPage(page: number, size: number) {
  return useQuery({
    queryKey: localPokemonKeys.page(page, size),
    queryFn: ({ signal }) => get<Page<LocalPokemon>>(`/local-pokemon?page=${page}&size=${size}`, signal),
    placeholderData: keepPreviousData,
  })
}

export function useLocalPokemon(id: number) {
  return useQuery({
    queryKey: localPokemonKeys.detail(id),
    queryFn: ({ signal }) => get<LocalPokemon>(`/local-pokemon/${id}`, signal),
  })
}

export interface SyncOutcome {
  pokemon: LocalPokemon
  /** true for a first copy (201), false when an existing copy was refreshed (200) */
  created: boolean
}

/**
 * Every write puts the server's answer straight into the detail cache (no refetch for the Pokémon just written) and
 * invalidates the list pages: the visible one refetches, the others refetch when next shown.
 */
function useWriteSuccess() {
  const queryClient = useQueryClient()
  return (pokemon: LocalPokemon) => {
    queryClient.setQueryData(localPokemonKeys.detail(pokemon.id), pokemon)
    return queryClient.invalidateQueries({ queryKey: localPokemonKeys.pages })
  }
}

/** US03: copy (or refresh) a Pokémon from PokeAPI into the local store. ADMIN only. */
export function useSyncPokemon() {
  const onWritten = useWriteSuccess()
  return useMutation({
    mutationFn: async (pokedexNumber: number): Promise<SyncOutcome> => {
      const { status, data } = await request<LocalPokemon>(`/local-pokemon/sync/${pokedexNumber}`, { method: 'POST' })
      return { pokemon: data, created: status === 201 }
    },
    onSuccess: ({ pokemon }) => onWritten(pokemon),
  })
}

/** US04: a Pokémon that exists only locally. ADMIN only. */
export function useCreateLocalPokemon() {
  const onWritten = useWriteSuccess()
  return useMutation({
    mutationFn: async (body: CreateLocalPokemonRequest) =>
      (await request<LocalPokemon>('/local-pokemon', { method: 'POST', body })).data,
    onSuccess: onWritten,
  })
}

/** US04: a full replacement, carrying the version that was read (a stale one is a 409). ADMIN only. */
export function useUpdateLocalPokemon(id: number) {
  const onWritten = useWriteSuccess()
  return useMutation({
    mutationFn: async (body: UpdateLocalPokemonRequest) =>
      (await request<LocalPokemon>(`/local-pokemon/${id}`, { method: 'PUT', body })).data,
    onSuccess: onWritten,
  })
}

/** US04. ADMIN only. */
export function useDeleteLocalPokemon() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (id: number) => {
      await request<void>(`/local-pokemon/${id}`, { method: 'DELETE' })
    },
    onSuccess: (_, id) => {
      queryClient.removeQueries({ queryKey: localPokemonKeys.detail(id) })
      return queryClient.invalidateQueries({ queryKey: localPokemonKeys.pages })
    },
  })
}
