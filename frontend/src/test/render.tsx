import { QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { App } from '../App'
import { createQueryClient } from '../api/queryClient'
import { sessionStore } from '../auth/session'
import { type DemoUser, USERS, tokenOf } from './fakeApi'

/** Shows the router's current location, so tests can assert on navigation and on the `?page=` URL state. */
function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{location.pathname + location.search}</div>
}

interface Options {
  /** sign in as a demo user before rendering */
  as?: DemoUser
}

/** Renders the whole app (real routes, query client and API client) at `path`, against the fake API. */
export function renderApp(path: string, { as }: Options = {}) {
  if (as) {
    const { password: _, ...user } = USERS[as]
    sessionStore.signIn(tokenOf(as), 3600, user)
  }
  const queryClient = createQueryClient()
  queryClient.setDefaultOptions({ queries: { ...queryClient.getDefaultOptions().queries, retry: false } })
  const user = userEvent.setup()
  const result = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...result, user, queryClient }
}
