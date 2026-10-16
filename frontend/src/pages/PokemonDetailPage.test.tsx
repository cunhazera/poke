import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fake } from '../test/fakeApi'
import { renderApp } from '../test/render'

describe('Pokémon detail (US02)', () => {
  it('shows the artwork, description, measures, types and abilities', async () => {
    renderApp('/pokemon/133')

    expect(await screen.findByRole('heading', { level: 1, name: 'Eevee' })).toBeInTheDocument()
    expect(screen.getByRole('img', { name: 'Eevee artwork' })).toHaveAttribute('src', 'https://artwork.test/133.png')
    expect(screen.getByText('#133')).toBeInTheDocument()
    expect(screen.getByText('Evolution Pokémon')).toBeInTheDocument()
    expect(screen.getByText('Pokédex entry for eevee.')).toBeInTheDocument()
    expect(screen.getByText('0.3 m')).toBeInTheDocument()
    expect(screen.getByText('6.9 kg')).toBeInTheDocument()
    expect(screen.getByText('Normal')).toBeInTheDocument()
  })

  it('shows every base stat as a meter, with the total', async () => {
    renderApp('/pokemon/133')

    const hp = await screen.findByRole('meter', { name: 'HP' })
    expect(hp).toHaveAttribute('value', '55')
    expect(screen.getByRole('meter', { name: 'Sp. Def' })).toHaveAttribute('value', '65')
    expect(screen.getAllByRole('meter')).toHaveLength(6)
    expect(screen.getByText('325')).toBeInTheDocument()
  })

  it('draws a branching evolution chain with a link per stage', async () => {
    renderApp('/pokemon/133')

    const evolution = (await screen.findByRole('heading', { name: 'Evolution' })).closest('section')!
    for (const [name, id] of [['Vaporeon', 134], ['Jolteon', 135], ['Flareon', 136]] as const) {
      expect(within(evolution).getByRole('link', { name })).toHaveAttribute('href', `/pokemon/${id}`)
    }
    expect(within(evolution).getByText('Eevee').closest('[aria-current]')).toHaveAttribute('aria-current', 'page')
  })

  it('moves to the previous and next Pokémon', async () => {
    const { user } = renderApp('/pokemon/2')
    await screen.findByRole('heading', { level: 1, name: 'Ivysaur' })

    await user.click(screen.getByRole('link', { name: /next pokémon/i }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Venusaur' })).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: /previous pokémon/i }))
    expect(await screen.findByRole('heading', { level: 1, name: 'Ivysaur' })).toBeInTheDocument()
  })

  it('says so when no species has that number', async () => {
    renderApp('/pokemon/1026')

    expect(await screen.findByRole('heading', { name: 'Pokémon not found' })).toBeInTheDocument()
  })

  it('rejects a malformed id without calling the API', async () => {
    renderApp('/pokemon/abc')

    expect(await screen.findByRole('heading', { name: 'Pokémon not found' })).toBeInTheDocument()
    expect(fake.requests).toHaveLength(0)
  })

  it('offers no sync to visitors or plain users', async () => {
    renderApp('/pokemon/4', { as: 'ash' })

    await screen.findByRole('heading', { level: 1, name: 'Charmander' })
    expect(screen.queryByRole('button', { name: /local store/i })).not.toBeInTheDocument()
  })

  it('lets an admin copy the Pokémon into the local store (US03)', async () => {
    const { user } = renderApp('/pokemon/4', { as: 'oak' })
    await screen.findByRole('heading', { level: 1, name: 'Charmander' })

    await user.click(screen.getByRole('button', { name: 'Sync to local store' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Charmander was added to the local store.')
    expect(screen.getByRole('link', { name: 'View local copy' })).toHaveAttribute('href', '/local/3')
    expect(fake.local.map((p) => p.pokedexNumber)).toContain(4)
  })

  it('tells an admin when the sync refreshed an existing copy', async () => {
    const { user } = renderApp('/pokemon/25', { as: 'oak' })
    await screen.findByRole('heading', { level: 1, name: 'Pikachu' })

    await user.click(screen.getByRole('button', { name: 'Sync to local store' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Pikachu was refreshed from PokeAPI; its proprietary data was kept.')
  })
})
