import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fake } from '../test/fakeApi'
import { renderApp } from '../test/render'

describe('Local Pokémon detail (US03/US04)', () => {
  it('shows the catalog data and our proprietary data', async () => {
    renderApp('/local/1', { as: 'ash' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Pikachu' })).toBeInTheDocument()
    const proprietary = screen.getByRole('heading', { name: 'Proprietary data' }).closest('section')!
    expect(within(proprietary).getByText('ピカチュウ')).toBeInTheDocument()
    expect(within(proprietary).getByText('ja')).toBeInTheDocument()
    expect(within(proprietary).getByText('Kanto')).toBeInTheDocument()
    expect(within(proprietary).getByText('forest')).toBeInTheDocument()
    expect(within(proprietary).getByText('35.6762, 139.6503')).toBeInTheDocument()
    expect(within(proprietary).getByText('mascot')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View in Pokédex' })).toHaveAttribute('href', '/pokemon/25')
    expect(screen.getAllByRole('meter')).toHaveLength(6)
  })

  it('shows placeholders for a local-only Pokémon without proprietary data or images', async () => {
    renderApp('/local/2', { as: 'ash' })

    expect(await screen.findByRole('heading', { level: 1, name: 'Missingno' })).toBeInTheDocument()
    expect(screen.getByText('Created locally')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'View in Pokédex' })).not.toBeInTheDocument()
    expect(screen.getAllByText('Not set').length).toBeGreaterThan(0)
  })

  it('offers no edits to a plain user', async () => {
    renderApp('/local/1', { as: 'ash' })

    await screen.findByRole('heading', { level: 1, name: 'Pikachu' })
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  it('deletes only after the admin confirms', async () => {
    const { user } = renderApp('/local/1', { as: 'oak' })
    await screen.findByRole('heading', { level: 1, name: 'Pikachu' })

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    const confirm = screen.getByRole('alertdialog', { name: 'Delete Pikachu from the local store?' })
    await user.click(within(confirm).getByRole('button', { name: 'Cancel' }))
    expect(fake.local.map((p) => p.id)).toContain(1)

    await user.click(screen.getByRole('button', { name: 'Delete' }))
    await user.click(screen.getByRole('button', { name: 'Delete permanently' }))

    expect(await screen.findByRole('heading', { name: 'Local store' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Pikachu' })).not.toBeInTheDocument()
    expect(fake.local.map((p) => p.id)).not.toContain(1)
  })

  it('lets an admin re-sync a synced Pokémon, keeping its proprietary data', async () => {
    const { user } = renderApp('/local/1', { as: 'oak' })
    await screen.findByRole('heading', { level: 1, name: 'Pikachu' })

    await user.click(screen.getByRole('button', { name: 'Re-sync from PokeAPI' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Pikachu was refreshed from PokeAPI; its proprietary data was kept.')
    expect(screen.getByText('Kanto')).toBeInTheDocument()
  })

  it('says so when the local Pokémon does not exist', async () => {
    renderApp('/local/99', { as: 'ash' })

    expect(await screen.findByRole('heading', { name: 'Pokémon not found' })).toBeInTheDocument()
  })
})
