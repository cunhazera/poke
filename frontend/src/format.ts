import type { StatName } from './api/types'

const STAT_LABELS: Record<StatName, string> = {
  hp: 'HP', attack: 'Attack', defense: 'Defense', specialAttack: 'Sp. Atk', specialDefense: 'Sp. Def', speed: 'Speed',
}
/** The six base stats, in the games' order. */
export const STAT_NAMES = Object.keys(STAT_LABELS) as StatName[]
export const statLabel = (stat: StatName) => STAT_LABELS[stat]

/** `mr-mime` → `Mr Mime`: API names are lowercase slugs. */
export function displayName(slug: string): string {
  return slug.split('-').filter(Boolean).map((word) => word[0].toUpperCase() + word.slice(1)).join(' ')
}

/** 25 → `#025`, the Pokédex's own style. */
export function dexNumber(id: number): string {
  return `#${String(id).padStart(3, '0')}`
}

export const kilograms = (kg: number) => `${kg} kg`
export const metres = (m: number) => `${m} m`

export function dateTime(iso: string): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

/** A path segment that must be a positive integer id; anything else is "not found" without asking the API. */
export function parseId(raw: string | undefined): number | null {
  if (!raw || !/^[1-9][0-9]{0,9}$/.test(raw)) return null
  const id = Number(raw)
  return id <= 2_147_483_647 ? id : null
}

/** `?page=` in the URL is 1-based for people; the API's is zero-based. A malformed value means the first page. */
export function pageFromSearch(raw: string | null): number {
  const page = raw && /^[1-9][0-9]{0,5}$/.test(raw) ? Number(raw) : 1
  return page - 1
}
