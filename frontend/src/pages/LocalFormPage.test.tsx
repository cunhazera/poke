import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { fake, failWith } from '../test/fakeApi'
import { renderApp } from '../test/render'

const lastRequest = () => fake.requests.at(-1)!

describe('Create and edit local Pokémon (US04)', () => {
  it('is for admins only', async () => {
    renderApp('/local/new', { as: 'ash' })

    expect(await screen.findByRole('heading', { name: 'Admins only' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Create Pokémon' })).not.toBeInTheDocument()
  })

  it('creates a local-only Pokémon from every field and opens it', async () => {
    const { user } = renderApp('/local/new', { as: 'oak' })
    await screen.findByRole('heading', { name: 'New Pokémon' })

    await user.type(screen.getByLabelText('Name'), 'glitchy')
    await user.type(screen.getByLabelText('Category'), 'Glitch Pokémon')
    await user.type(screen.getByLabelText('Height (m)'), '1.2')
    await user.type(screen.getByLabelText('Weight (kg)'), '40.5')
    await user.selectOptions(screen.getByLabelText('Primary type'), 'ghost')
    await user.selectOptions(screen.getByLabelText('Secondary type'), 'dark')
    await user.type(screen.getByLabelText('Ability 1'), 'pressure')
    await user.click(screen.getByLabelText('Ability 1 is hidden'))
    for (const [label, value] of [['HP', '50'], ['Attack', '60'], ['Defense', '70'], ['Sp. Atk', '80'], ['Sp. Def', '90'], ['Speed', '100']]) {
      await user.clear(screen.getByLabelText(label))
      await user.type(screen.getByLabelText(label), value)
    }
    await user.type(screen.getByLabelText('Region'), 'Kanto')
    await user.type(screen.getByLabelText('Tags'), 'glitch, rare')
    await user.click(screen.getByRole('button', { name: 'Add a localized name' }))
    await user.type(screen.getByLabelText('Language 1'), 'ja')
    await user.type(screen.getByLabelText('Localized name 1'), 'バグ')
    await user.click(screen.getByRole('button', { name: 'Create Pokémon' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Glitchy' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/local/3')
    expect(lastRequest()).toMatchObject({ method: 'POST', path: '/local-pokemon' })
    expect(lastRequest().body).toEqual({
      name: 'glitchy', category: 'Glitch Pokémon', description: null, heightM: 1.2, weightKg: 40.5,
      spriteUrl: null, imageUrl: null, types: ['ghost', 'dark'], abilities: [{ name: 'pressure', hidden: true }],
      stats: { hp: 50, attack: 60, defense: 70, specialAttack: 80, specialDefense: 90, speed: 100 },
      localizedNames: { ja: 'バグ' }, region: 'Kanto', habitat: null, coordinates: null, tags: ['glitch', 'rare'],
    })
  })

  it('shows a rule the server rejected next to its field', async () => {
    failWith('post', '/local-pokemon', 400, 'Height must have at most one decimal', { field: 'heightM' })
    const { user } = renderApp('/local/new', { as: 'oak' })
    await screen.findByRole('heading', { name: 'New Pokémon' })

    await user.type(screen.getByLabelText('Name'), 'glitchy')
    await user.type(screen.getByLabelText('Height (m)'), '1.25')
    await user.type(screen.getByLabelText('Weight (kg)'), '1')
    await user.type(screen.getByLabelText('Ability 1'), 'pressure')
    await user.click(screen.getByRole('button', { name: 'Create Pokémon' }))

    const height = screen.getByLabelText('Height (m)')
    expect(await screen.findByRole('alert')).toHaveTextContent('Please fix the highlighted fields.')
    expect(height).toHaveAttribute('aria-invalid', 'true')
    expect(height).toHaveAccessibleDescription('Height must have at most one decimal')
  })

  it('asks for both coordinates or neither, before calling the API', async () => {
    const { user } = renderApp('/local/1/edit', { as: 'oak' })
    await screen.findByRole('heading', { name: 'Edit Pikachu' })
    const before = fake.requests.length

    await user.clear(screen.getByLabelText('Longitude'))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(screen.getByLabelText('Latitude')).toHaveAccessibleDescription('Enter both latitude and longitude, or neither.')
    expect(fake.requests).toHaveLength(before)
  })

  it('edits only the proprietary fields of a synced Pokémon, sending the version it read', async () => {
    const { user } = renderApp('/local/1/edit', { as: 'oak' })
    await screen.findByRole('heading', { name: 'Edit Pikachu' })

    expect(screen.queryByLabelText('Name')).not.toBeInTheDocument()
    expect(screen.getByText(/catalog fields come from PokeAPI/i)).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Region'))
    await user.type(screen.getByLabelText('Region'), 'Johto')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    expect(await screen.findByRole('heading', { level: 1, name: 'Pikachu' })).toBeInTheDocument()
    expect(screen.getByText('Johto')).toBeInTheDocument()
    expect(lastRequest()).toMatchObject({ method: 'PUT', path: '/local-pokemon/1' })
    expect(lastRequest().body).toEqual({
      version: 0, localizedNames: { ja: 'ピカチュウ' }, region: 'Johto', habitat: 'forest',
      coordinates: { latitude: 35.6762, longitude: 139.6503 }, tags: ['mascot'],
    })
  })

  it('edits every field of a local-only Pokémon', async () => {
    const { user } = renderApp('/local/2/edit', { as: 'oak' })
    await screen.findByRole('heading', { name: 'Edit Missingno' })

    expect(screen.getByLabelText('Name')).toHaveValue('missingno')
    expect(screen.getByLabelText('Attack')).toHaveValue(136)
    await user.clear(screen.getByLabelText('Weight (kg)'))
    await user.type(screen.getByLabelText('Weight (kg)'), '1000')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    await screen.findByRole('heading', { level: 1, name: 'Missingno' })
    expect(lastRequest().body).toMatchObject({ version: 0, name: 'missingno', weightKg: 1000, types: ['normal'], tags: ['glitch'] })
  })

  it('warns when someone else changed the Pokémon meanwhile, and reloads their version', async () => {
    const { user } = renderApp('/local/1/edit', { as: 'oak' })
    await screen.findByRole('heading', { name: 'Edit Pikachu' })
    Object.assign(fake.local[0], { region: 'Alola', version: 1 })

    await user.clear(screen.getByLabelText('Region'))
    await user.type(screen.getByLabelText('Region'), 'Johto')
    await user.click(screen.getByRole('button', { name: 'Save changes' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('Someone else changed this Pokémon while you were editing.')
    await user.click(within(alert).getByRole('button', { name: 'Load the latest version' }))

    expect(await screen.findByDisplayValue('Alola')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await screen.findByRole('heading', { level: 1, name: 'Pikachu' })
    expect(lastRequest().body).toMatchObject({ version: 1, region: 'Alola' })
  })
})
