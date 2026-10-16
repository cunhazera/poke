import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fake } from '../test/fakeApi'
import { renderApp } from '../test/render'

const cardNames = () => screen.getAllByRole('article').map((card) => within(card).getByRole('heading').textContent)

describe('Searching the Pokédex by name', () => {
  it('filters the list as the user types, keeping the search in the URL', async () => {
    const { user } = renderApp('/')
    await screen.findByRole('heading', { name: 'Bulbasaur' })

    await user.type(screen.getByRole('searchbox', { name: 'Search the Pokédex' }), 'char')

    expect(await screen.findByText('3 matches for “char”.')).toBeInTheDocument()
    expect(cardNames()).toEqual(['Charmander', 'Charmeleon', 'Charizard'])
    expect(screen.getByTestId('location')).toHaveTextContent('/?name=char')
  })

  it('waits for the user to pause instead of searching on every keystroke', async () => {
    const { user } = renderApp('/')
    await screen.findByRole('heading', { name: 'Bulbasaur' })

    await user.type(screen.getByRole('searchbox', { name: 'Search the Pokédex' }), 'charm')
    await screen.findByText('2 matches for “charm”.')

    expect(fake.requests.filter((r) => r.path.includes('name=')).map((r) => r.path)).toEqual(['/pokemon?page=0&size=20&name=charm'])
  })

  it('opens a search from the URL, and starts again from the first page', async () => {
    const { user } = renderApp('/?page=3')
    await screen.findByText('Page 3 of 8')

    await user.type(screen.getByRole('searchbox', { name: 'Search the Pokédex' }), 'Mr. Mime')

    expect(await screen.findByRole('heading', { name: 'Mr Mime' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/?name=Mr.+Mime')
  })

  it('restores a search from a link', async () => {
    renderApp('/?name=saur')

    expect(await screen.findByRole('heading', { name: 'Venusaur' })).toBeInTheDocument()
    expect(cardNames()).toEqual(['Bulbasaur', 'Ivysaur', 'Venusaur'])
    expect(screen.getByRole('searchbox', { name: 'Search the Pokédex' })).toHaveValue('saur')
  })

  it('says when nothing matches, and clears the search', async () => {
    const { user } = renderApp('/?name=zzz')

    expect(await screen.findByText('No Pokémon match “zzz”.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Clear search' }))

    expect(await screen.findByRole('heading', { name: 'Bulbasaur' })).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Search the Pokédex' })).toHaveValue('')
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })

  it('jumps straight to a Pokémon when a number is entered', async () => {
    const { user } = renderApp('/')
    await screen.findByRole('heading', { name: 'Bulbasaur' })

    await user.type(screen.getByRole('searchbox', { name: 'Search the Pokédex' }), '25{Enter}')

    expect(await screen.findByRole('heading', { level: 1, name: 'Pikachu' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/pokemon/25')
  })

  it('explains a search the API rejects, next to the box', async () => {
    renderApp('/?name=...')

    expect(await screen.findByRole('alert')).toHaveTextContent('Search text must contain a letter or a digit')
    expect(screen.getByRole('searchbox', { name: 'Search the Pokédex' })).toBeInTheDocument()
  })
})

describe('Searching the local store', () => {
  it('finds a Pokémon by a localized name', async () => {
    const { user } = renderApp('/local', { as: 'ash' })
    await screen.findByRole('heading', { name: 'Missingno' })

    await user.type(screen.getByRole('searchbox', { name: 'Search the local store' }), 'ピカ')

    await screen.findByText('1 match for “ピカ”.')
    expect(cardNames()).toEqual(['Pikachu'])
    expect(screen.getByTestId('location')).toHaveTextContent('/local?q=')
  })

  it('keeps the search when coming back from a Pokémon', async () => {
    const { user } = renderApp('/local?q=missing', { as: 'ash' })
    await user.click(await screen.findByRole('link', { name: 'Missingno' }))
    await screen.findByRole('heading', { level: 1, name: 'Missingno' })

    await user.click(screen.getByRole('link', { name: '← Local store' }))

    expect(await screen.findByRole('heading', { name: 'Missingno' })).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Search the local store' })).toHaveValue('missing')
  })
})

describe('The search box', () => {
  it('empties when a link leads back to the whole list', async () => {
    const { user } = renderApp('/?name=saur')
    await screen.findByText('3 matches for “saur”.')
    await user.click(screen.getByRole('link', { name: /ivysaur/i }))
    await screen.findByRole('heading', { level: 1, name: 'Ivysaur' })

    await user.click(screen.getByRole('link', { name: 'All Pokémon' }))

    expect(await screen.findByText('151 species, in Pokédex order.')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: 'Search the Pokédex' })).toHaveValue('')
  })
})
