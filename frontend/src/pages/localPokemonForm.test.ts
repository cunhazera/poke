import { describe, expect, it } from 'vitest'
import type { LocalPokemon } from '../api/types'
import { checkValues, emptyValues, errorAt, toCreateRequest, toUpdateRequest, valuesOf } from './localPokemonForm'

const pikachu: LocalPokemon = {
  id: 1, pokedexNumber: 25, name: 'pikachu', category: 'Mouse Pokémon', description: 'Electric.', heightM: 0.4, weightKg: 6,
  spriteUrl: 'https://s/25.png', imageUrl: null, types: ['electric'], abilities: [{ name: 'static', hidden: false }],
  stats: { hp: 35, attack: 55, defense: 40, specialAttack: 50, specialDefense: 50, speed: 90, total: 320 },
  localizedNames: { ja: 'ピカチュウ', fr: 'Pikachu' }, region: 'Kanto', habitat: null,
  coordinates: { latitude: 35.6762, longitude: 139.6503 }, tags: ['mascot', 'gen-1'], syncedAt: '2026-10-16T00:00:00Z', version: 3,
}

describe('local Pokémon form mapping', () => {
  it('round-trips a Pokémon through the form unchanged', () => {
    const { id: _, pokedexNumber: __, syncedAt: ___, version: ____, stats, ...rest } = pikachu
    const { total: _____, ...editableStats } = stats

    expect(toCreateRequest(valuesOf(pikachu))).toEqual({ ...rest, stats: editableStats })
  })

  it('sends only the version and the proprietary fields for a synced Pokémon', () => {
    const body = toUpdateRequest(valuesOf(pikachu), pikachu)

    expect(Object.keys(body).sort()).toEqual(['coordinates', 'habitat', 'localizedNames', 'region', 'tags', 'version'])
    expect(body.version).toBe(3)
  })

  it('sends the catalog fields too for a Pokémon created locally', () => {
    const local = { ...pikachu, pokedexNumber: null }

    expect(toUpdateRequest(valuesOf(local), local)).toMatchObject({ version: 3, name: 'pikachu', types: ['electric'] })
  })

  it('clears blank optional fields, drops empty rows and trims what was typed', () => {
    const values = {
      ...emptyValues(),
      name: '  glitchy ', region: '   ', tags: ' a, ,b ,',
      abilities: [{ name: ' ', hidden: true }, { name: 'levitate ', hidden: false }, { name: '', hidden: false }],
      localizedNames: [{ language: '', name: '' }, { language: ' ja', name: 'バグ ' }],
    }

    expect(toCreateRequest(values)).toMatchObject({
      name: 'glitchy', region: null, category: null, tags: ['a', 'b'],
      abilities: [{ name: 'levitate', hidden: false }], localizedNames: { ja: 'バグ' }, coordinates: null,
    })
  })

  it('sends a missing number as null so the server names the field, never as 0', () => {
    expect(toCreateRequest(emptyValues())).toMatchObject({ heightM: null, weightKg: null, stats: { hp: null, speed: null } })
  })

  it('catches what the server would report vaguely: half a coordinate, a repeated type or language', () => {
    expect(checkValues({ ...emptyValues(), latitude: '10' })).toEqual({ coordinates: 'Enter both latitude and longitude, or neither.' })
    expect(checkValues({ ...emptyValues(), primaryType: 'fire', secondaryType: 'fire' })).toHaveProperty(['types[1]'])
    expect(checkValues({ ...emptyValues(), localizedNames: [{ language: 'ja', name: 'a' }, { language: 'ja', name: 'b' }] }))
      .toHaveProperty('localizedNames')
    expect(checkValues(valuesOf(pikachu))).toEqual({})
  })

  it('finds the error for a field or anything nested under it', () => {
    const errors = { 'stats.hp': 'too high', 'types[1]': 'unknown type', coordinates: 'both or neither' }

    expect(errorAt(errors, 'stats')).toBe('too high')
    expect(errorAt(errors, 'types[0]', 'types')).toBe('unknown type')
    expect(errorAt(errors, 'coordinates.latitude', 'coordinates')).toBe('both or neither')
    expect(errorAt(errors, 'stat')).toBeUndefined()
  })
})
