import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fake } from '../test/fakeApi'
import { renderApp } from '../test/render'

const row = (name: string) => screen.getByRole('heading', { name }).closest('article')!

describe('Local store list (US03)', () => {
  it('lists the local copies, telling synced from local-only ones', async () => {
    renderApp('/local', { as: 'ash' })

    await screen.findByRole('heading', { name: 'Pikachu' })
    expect(within(row('Pikachu')).getByText('#025')).toBeInTheDocument()
    expect(within(row('Pikachu')).getByText('Synced')).toBeInTheDocument()
    expect(within(row('Pikachu')).getByText('Kanto')).toBeInTheDocument()
    expect(within(row('Pikachu')).getByText('mascot')).toBeInTheDocument()
    expect(within(row('Missingno')).getByText('Local only')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /pikachu/i })).toHaveAttribute('href', '/local/1')
  })

  it('is read-only for a plain user', async () => {
    renderApp('/local', { as: 'ash' })

    await screen.findByRole('heading', { name: 'Pikachu' })
    expect(screen.queryByRole('button', { name: 'Sync' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'New Pokémon' })).not.toBeInTheDocument()
  })

  it('lets an admin sync a Pokémon by its Pokédex number', async () => {
    const { user } = renderApp('/local', { as: 'oak' })
    await screen.findByRole('heading', { name: 'Pikachu' })

    await user.type(screen.getByLabelText('Pokédex number'), '4')
    await user.click(screen.getByRole('button', { name: 'Sync' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Charmander was added to the local store.')
    expect(await screen.findByRole('heading', { name: 'Charmander' })).toBeInTheDocument()
    expect(screen.getByLabelText('Pokédex number')).toHaveValue(null)
  })

  it('checks the number before calling the API', async () => {
    const { user } = renderApp('/local', { as: 'oak' })
    await screen.findByRole('heading', { name: 'Pikachu' })
    const before = fake.requests.length

    await user.type(screen.getByLabelText('Pokédex number'), '0')
    await user.click(screen.getByRole('button', { name: 'Sync' }))

    expect(screen.getByText('Enter a Pokédex number from 1 to 1025.')).toBeInTheDocument()
    expect(fake.requests).toHaveLength(before)
  })

  it('shows why a sync failed', async () => {
    const { user } = renderApp('/local', { as: 'oak' })
    await screen.findByRole('heading', { name: 'Pikachu' })

    await user.type(screen.getByLabelText('Pokédex number'), '999')
    await user.click(screen.getByRole('button', { name: 'Sync' }))

    expect(await screen.findByText('Pokemon 999 not found')).toBeInTheDocument()
  })

  it('links an admin to the create form', async () => {
    const { user } = renderApp('/local', { as: 'oak' })

    await user.click(await screen.findByRole('link', { name: 'New Pokémon' }))

    expect(await screen.findByRole('heading', { name: 'New Pokémon' })).toBeInTheDocument()
  })
})
