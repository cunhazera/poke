import { useMutation, useQueryClient } from '@tanstack/react-query'
import { sessionStore } from '../auth/session'
import { request } from './client'
import { localPokemonKeys } from './localPokemon'
import type { TokenResponse, User } from './types'

export interface Credentials {
  login: string
  password: string
}

export interface Registration {
  username: string
  email: string
  password: string
}

/** Logs in, then asks who the token belongs to (the role decides what the UI offers). */
async function signIn(credentials: Credentials): Promise<User> {
  const { data: token } = await request<TokenResponse>('/auth/login', { method: 'POST', body: credentials })
  const { data: user } = await request<User>('/auth/me', { token: token.accessToken })
  sessionStore.signIn(token.accessToken, token.expiresIn, user)
  return user
}

export function useSignIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: signIn,
    // Data cached for the previous user (the local store) must not leak to the next one.
    onSuccess: () => queryClient.removeQueries({ queryKey: localPokemonKeys.all }),
  })
}

/** Registers (always a USER) and signs the new user in. */
export function useRegister() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async (registration: Registration) => {
      await request<User>('/auth/register', { method: 'POST', body: registration })
      return signIn({ login: registration.username, password: registration.password })
    },
    onSuccess: () => queryClient.removeQueries({ queryKey: localPokemonKeys.all }),
  })
}

export function useSignOut() {
  const queryClient = useQueryClient()
  return () => {
    sessionStore.signOut()
    queryClient.removeQueries({ queryKey: localPokemonKeys.all })
  }
}
