import type {
  CatalogFields, CreateLocalPokemonRequest, LocalPokemon, ProprietaryFields, StatName, UpdateLocalPokemonRequest,
} from '../api/types'

/**
 * The form's state is text, as typed; it becomes the API's payload only on submit. The rules themselves stay on the
 * server (one copy of them, D52): the form only shapes the payload and catches what the server can't explain well.
 */
export interface FormValues {
  name: string
  category: string
  description: string
  heightM: string
  weightKg: string
  spriteUrl: string
  imageUrl: string
  primaryType: string
  secondaryType: string
  abilities: { name: string; hidden: boolean }[]
  stats: Record<StatName, string>
  localizedNames: { language: string; name: string }[]
  region: string
  habitat: string
  latitude: string
  longitude: string
  tags: string
}

export const MAX_ABILITIES = 3

const EMPTY_STATS: Record<StatName, string> = { hp: '', attack: '', defense: '', specialAttack: '', specialDefense: '', speed: '' }

export function emptyValues(): FormValues {
  return {
    name: '', category: '', description: '', heightM: '', weightKg: '', spriteUrl: '', imageUrl: '',
    primaryType: 'normal', secondaryType: '',
    abilities: Array.from({ length: MAX_ABILITIES }, () => ({ name: '', hidden: false })),
    stats: { ...EMPTY_STATS },
    localizedNames: [], region: '', habitat: '', latitude: '', longitude: '', tags: '',
  }
}

export function valuesOf(pokemon: LocalPokemon): FormValues {
  const { total: _, ...stats } = pokemon.stats
  return {
    name: pokemon.name,
    category: pokemon.category ?? '',
    description: pokemon.description ?? '',
    heightM: String(pokemon.heightM),
    weightKg: String(pokemon.weightKg),
    spriteUrl: pokemon.spriteUrl ?? '',
    imageUrl: pokemon.imageUrl ?? '',
    primaryType: pokemon.types[0] ?? 'normal',
    secondaryType: pokemon.types[1] ?? '',
    abilities: Array.from({ length: MAX_ABILITIES }, (_, i) => ({ ...(pokemon.abilities[i] ?? { name: '', hidden: false }) })),
    stats: Object.fromEntries(Object.entries(stats).map(([stat, value]) => [stat, String(value)])) as Record<StatName, string>,
    localizedNames: Object.entries(pokemon.localizedNames).map(([language, name]) => ({ language, name })),
    region: pokemon.region ?? '',
    habitat: pokemon.habitat ?? '',
    latitude: pokemon.coordinates ? String(pokemon.coordinates.latitude) : '',
    longitude: pokemon.coordinates ? String(pokemon.coordinates.longitude) : '',
    tags: pokemon.tags.join(', '),
  }
}

const text = (value: string) => value.trim() || null
/** An empty number field is sent as null, so the server names the missing field; anything typed is sent as typed. */
const number = (value: string) => (value.trim() === '' ? null : Number(value)) as number

function catalogFields(values: FormValues): CatalogFields {
  return {
    name: values.name.trim(),
    category: text(values.category),
    description: text(values.description),
    heightM: number(values.heightM),
    weightKg: number(values.weightKg),
    spriteUrl: text(values.spriteUrl),
    imageUrl: text(values.imageUrl),
    types: [values.primaryType, values.secondaryType].filter(Boolean),
    abilities: values.abilities.filter((a) => a.name.trim()).map((a) => ({ name: a.name.trim(), hidden: a.hidden })),
    stats: Object.fromEntries(Object.entries(values.stats).map(([stat, value]) => [stat, number(value)])) as CatalogFields['stats'],
  }
}

function proprietaryFields(values: FormValues): ProprietaryFields {
  const hasCoordinates = values.latitude.trim() !== '' && values.longitude.trim() !== ''
  return {
    localizedNames: Object.fromEntries(
      values.localizedNames.filter((n) => n.language.trim() || n.name.trim()).map((n) => [n.language.trim(), n.name.trim()]),
    ),
    region: text(values.region),
    habitat: text(values.habitat),
    coordinates: hasCoordinates ? { latitude: Number(values.latitude), longitude: Number(values.longitude) } : null,
    tags: values.tags.split(',').map((tag) => tag.trim()).filter(Boolean),
  }
}

export function toCreateRequest(values: FormValues): CreateLocalPokemonRequest {
  return { ...catalogFields(values), ...proprietaryFields(values) }
}

/** A synced Pokémon's catalog fields belong to PokeAPI (D47): its update carries only the proprietary ones. */
export function toUpdateRequest(values: FormValues, original: LocalPokemon): UpdateLocalPokemonRequest {
  const catalog = original.pokedexNumber === null ? catalogFields(values) : {}
  return { version: original.version, ...catalog, ...proprietaryFields(values) }
}

/** Checks the server would only answer with a vague message; returns errors by field path. */
export function checkValues(values: FormValues): Record<string, string> {
  const errors: Record<string, string> = {}
  if ((values.latitude.trim() === '') !== (values.longitude.trim() === '')) {
    errors.coordinates = 'Enter both latitude and longitude, or neither.'
  }
  if (values.secondaryType && values.secondaryType === values.primaryType) {
    errors['types[1]'] = 'Pick a different secondary type, or none.'
  }
  const languages = values.localizedNames.map((n) => n.language.trim()).filter(Boolean)
  if (new Set(languages).size !== languages.length) errors.localizedNames = 'Each language can have only one name.'
  return errors
}

/** The first error for a field or anything below it: `stats` matches `stats.hp`, `types` matches `types[1]`. */
export function errorAt(errors: Record<string, string>, ...paths: string[]): string | undefined {
  for (const path of paths) {
    const key = Object.keys(errors).find((k) => k === path || k.startsWith(`${path}.`) || k.startsWith(`${path}[`))
    if (key) return errors[key]
  }
  return undefined
}
