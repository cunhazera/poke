import { describe, expect, it } from 'vitest'
import { dexNumber, displayName, pageFromSearch, parseId } from './format'

describe('format', () => {
  it('turns API slugs into display names', () => {
    expect(displayName('bulbasaur')).toBe('Bulbasaur')
    expect(displayName('mr-mime')).toBe('Mr Mime')
    expect(displayName('ho-oh')).toBe('Ho Oh')
  })

  it('pads Pokédex numbers to three digits, and leaves longer ones alone', () => {
    expect(dexNumber(7)).toBe('#007')
    expect(dexNumber(1025)).toBe('#1025')
  })

  it('accepts only positive integer ids that fit the API', () => {
    expect(parseId('25')).toBe(25)
    expect(parseId('2147483647')).toBe(2147483647)
    for (const bad of [undefined, '', '0', '-1', '01', '1.5', '+1', 'abc', '1e3', '2147483648']) {
      expect(parseId(bad)).toBeNull()
    }
  })

  it('reads the 1-based page from the URL as a zero-based API page', () => {
    expect(pageFromSearch(null)).toBe(0)
    expect(pageFromSearch('3')).toBe(2)
    for (const bad of ['0', '-2', 'abc', '1.5']) expect(pageFromSearch(bad)).toBe(0)
  })
})
