import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { sessionStore } from '../auth/session'
import { fake } from '../test/fakeApi'
import { renderApp } from '../test/render'

const header = () => screen.getByRole('banner')

describe('Sign in, register and sign out', () => {
  it('signs in by username and shows who is signed in and their role', async () => {
    const { user } = renderApp('/login')

    await user.type(screen.getByLabelText('Username or email'), 'oak')
    await user.type(screen.getByLabelText('Password'), 'professor123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await within(header()).findByText('oak')).toBeInTheDocument()
    expect(within(header()).getByText('Admin')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
    expect(sessionStore.get()?.token).toBe('token-oak')
  })

  it('reports a wrong password without signing in', async () => {
    const { user } = renderApp('/login')

    await user.type(screen.getByLabelText('Username or email'), 'ash@pallet.town')
    await user.type(screen.getByLabelText('Password'), 'wrong-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username, email or password')
    expect(sessionStore.get()).toBeNull()
  })

  it('sends a visitor to sign in first, then back to the protected page', async () => {
    const { user } = renderApp('/local')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    await user.type(screen.getByLabelText('Username or email'), 'ash')
    await user.type(screen.getByLabelText('Password'), 'pikachu123')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('heading', { name: 'Local store' })).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/local')
  })

  it('registers a new user and signs them in', async () => {
    const { user } = renderApp('/register')

    await user.type(screen.getByLabelText('Username'), 'misty')
    await user.type(screen.getByLabelText('Email'), 'misty@cerulean.city')
    await user.type(screen.getByLabelText('Password'), 'starmie123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await within(header()).findByText('misty')).toBeInTheDocument()
    expect(fake.users.map((u) => u.username)).toContain('misty')
  })

  it('shows the server’s reason next to the field it concerns', async () => {
    const { user } = renderApp('/register')

    await user.type(screen.getByLabelText('Username'), 'ASH')
    await user.type(screen.getByLabelText('Email'), 'other@pallet.town')
    await user.type(screen.getByLabelText('Password'), 'pikachu123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    const username = screen.getByLabelText('Username')
    expect(await screen.findByText("Username 'ASH' is already taken")).toBeInTheDocument()
    expect(username).toHaveAttribute('aria-invalid', 'true')
    expect(username).toHaveAccessibleDescription("Username 'ASH' is already taken")
  })

  it('signs out and leaves protected pages', async () => {
    const { user } = renderApp('/local', { as: 'ash' })
    await screen.findByRole('heading', { name: 'Local store' })

    await user.click(within(header()).getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(within(header()).getByRole('link', { name: 'Sign in' })).toBeInTheDocument()
  })
})
