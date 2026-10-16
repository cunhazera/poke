// The API's JSON contract (see the README's API section). Kept in one place so a contract change is a compile error.

export interface Ability {
  name: string
  hidden: boolean
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  totalItems: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

/** US01: one entry of the catalog listing. */
export interface PokemonSummary {
  id: number
  name: string
  spriteUrl: string | null
  category: string | null
  weightKg: number
  abilities: Ability[]
}

export interface BaseStats {
  hp: number
  attack: number
  defense: number
  specialAttack: number
  specialDefense: number
  speed: number
  total: number
}

export type StatName = Exclude<keyof BaseStats, 'total'>

export interface EvolutionStage {
  id: number
  name: string
  spriteUrl: string | null
  evolvesTo: EvolutionStage[]
}

/** US02: the detail view. */
export interface PokemonDetail {
  id: number
  name: string
  category: string | null
  imageUrl: string | null
  spriteUrl: string | null
  description: string | null
  heightM: number
  weightKg: number
  types: string[]
  abilities: Ability[]
  stats: BaseStats
  evolutionChain: EvolutionStage
}

export interface Coordinates {
  latitude: number
  longitude: number
}

/** US03: a Pokémon in our own store; `pokedexNumber` and `syncedAt` are null for one created locally. */
export interface LocalPokemon {
  id: number
  pokedexNumber: number | null
  name: string
  category: string | null
  description: string | null
  heightM: number
  weightKg: number
  spriteUrl: string | null
  imageUrl: string | null
  types: string[]
  abilities: Ability[]
  stats: BaseStats
  localizedNames: Record<string, string>
  region: string | null
  habitat: string | null
  coordinates: Coordinates | null
  tags: string[]
  syncedAt: string | null
  version: number
}

/** US04: the fields we own, editable on every local Pokémon. */
export interface ProprietaryFields {
  localizedNames: Record<string, string>
  region: string | null
  habitat: string | null
  coordinates: Coordinates | null
  tags: string[]
}

/** US04: the catalog fields, editable only on a Pokémon created locally. */
export interface CatalogFields {
  name: string
  category: string | null
  description: string | null
  heightM: number
  weightKg: number
  spriteUrl: string | null
  imageUrl: string | null
  types: string[]
  abilities: Ability[]
  stats: Omit<BaseStats, 'total'>
}

export type CreateLocalPokemonRequest = CatalogFields & ProprietaryFields

/** A full replacement: a synced Pokémon carries only the proprietary fields, a local one carries both. */
export type UpdateLocalPokemonRequest = { version: number } & ProprietaryFields & Partial<CatalogFields>

export type Role = 'USER' | 'ADMIN'

export interface User {
  id: number
  username: string
  email: string
  role: Role
}

export interface TokenResponse {
  accessToken: string
  tokenType: 'Bearer'
  expiresIn: number
}

/** RFC 9457 problem details, plus this API's `field` (one broken rule) and `errors` (missing fields) extensions. */
export interface ProblemDetail {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  field?: string
  errors?: Record<string, string>
}

/** The 18 canonical types the API accepts (D52). */
export const POKEMON_TYPES = [
  'normal', 'fire', 'water', 'electric', 'grass', 'ice', 'fighting', 'poison', 'ground',
  'flying', 'psychic', 'bug', 'rock', 'ghost', 'dragon', 'dark', 'steel', 'fairy',
] as const
