import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { failWith } from '../test/fakeApi'
import { renderApp } from '../test/render'

describe('Pokédex catalog (US01)', () => {
  it('lists the first page with sprite, number, category, weight and abilities', async () => {
    renderApp('/')

    const card = (await screen.findByRole('heading', { name: 'Bulbasaur' })).closest('article')!
    expect(within(card).getByRole('img', { name: 'Bulbasaur' })).toHaveAttribute('src', 'https://sprites.test/1.png')
    expect(within(card).getByText('#001')).toBeInTheDocument()
    expect(within(card).getByText('Seed Pokémon')).toBeInTheDocument()
    expect(within(card).getByText('6.9 kg')).toBeInTheDocument()
    expect(within(card).getByText('Overgrow')).toBeInTheDocument()
    expect(within(card).getByText('Chlorophyll')).toHaveAttribute('title', 'Hidden ability')
    expect(screen.getAllByRole('article')).toHaveLength(20)
    expect(screen.getByText('Page 1 of 8')).toBeInTheDocument()
  })

  it('links each entry to its detail page', async () => {
    renderApp('/')

    expect(await screen.findByRole('link', { name: /bulbasaur/i })).toHaveAttribute('href', '/pokemon/1')
  })

  it('pages forward and back, keeping the page in the URL', async () => {
    const { user } = renderApp('/')
    await screen.findByRole('heading', { name: 'Bulbasaur' })

    await user.click(screen.getByRole('button', { name: 'Next page' }))

    expect(await screen.findByRole('heading', { name: 'Species 21' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/?page=2')
    expect(screen.getByText('Page 2 of 8')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Previous page' }))

    expect(await screen.findByRole('heading', { name: 'Bulbasaur' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })

  it('opens the page in the URL and disables Next on the last one', async () => {
    renderApp('/?page=8')

    expect(await screen.findByRole('heading', { name: 'Species 151' })).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(11)
    expect(screen.getByRole('button', { name: 'Next page' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeEnabled()
  })

  it('treats a malformed page in the URL as the first page', async () => {
    renderApp('/?page=abc')

    expect(await screen.findByRole('heading', { name: 'Bulbasaur' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous page' })).toBeDisabled()
  })

  it('offers a way back from a page past the end', async () => {
    const { user } = renderApp('/?page=99')

    expect(await screen.findByText('There are no Pokémon on this page.')).toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Go to the first page' }))

    expect(await screen.findByRole('heading', { name: 'Bulbasaur' })).toBeInTheDocument()
  })

  it('explains an upstream outage and retries on request', async () => {
    failWith('get', '/pokemon', 503, 'The Pokémon catalog is temporarily unavailable', {}, { once: true })
    const { user } = renderApp('/')

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('The Pokémon catalog is temporarily unavailable')
    await user.click(within(alert).getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('heading', { name: 'Bulbasaur' })).toBeInTheDocument()
  })
})
