import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import type {
  BaseStats, EvolutionStage, LocalPokemon, PokemonDetail, PokemonSummary, ProblemDetail, User,
} from '../api/types'

/**
 * An in-memory stand-in for the Spring Boot API, served at the network level by MSW. It behaves like the real one
 * where the UI depends on it (pagination, tokens and roles, versions, the catalog-field rule, problem details), so
 * the tests drive the real fetch client, React Query and router instead of mocking them.
 */

export const CATALOG_SIZE = 151
const NAMES: Record<number, string> = {
  1: 'bulbasaur', 2: 'ivysaur', 3: 'venusaur', 4: 'charmander', 5: 'charmeleon', 6: 'charizard', 25: 'pikachu',
  122: 'mr-mime',
  133: 'eevee', 134: 'vaporeon', 135: 'jolteon', 136: 'flareon',
}
const nameOf = (id: number) => NAMES[id] ?? `species-${id}`
const sprite = (id: number) => `https://sprites.test/${id}.png`

export function summary(id: number): PokemonSummary {
  return {
    id,
    name: nameOf(id),
    spriteUrl: sprite(id),
    category: id === 133 ? 'Evolution Pokémon' : 'Seed Pokémon',
    weightKg: 6.9,
    abilities: [{ name: 'overgrow', hidden: false }, { name: 'chlorophyll', hidden: true }],
  }
}

const stage = (id: number, evolvesTo: EvolutionStage[] = []): EvolutionStage =>
  ({ id, name: nameOf(id), spriteUrl: sprite(id), evolvesTo })

const STATS: BaseStats = { hp: 55, attack: 55, defense: 50, specialAttack: 45, specialDefense: 65, speed: 55, total: 325 }

export function detail(id: number): PokemonDetail {
  const chain = id >= 133 && id <= 136
    ? stage(133, [stage(134), stage(135), stage(136)])
    : id <= 3 ? stage(1, [stage(2, [stage(3)])]) : stage(id)
  return {
    ...summary(id),
    imageUrl: `https://artwork.test/${id}.png`,
    description: `Pokédex entry for ${nameOf(id)}.`,
    heightM: 0.3,
    types: id === 133 ? ['normal'] : ['grass', 'poison'],
    stats: STATS,
    evolutionChain: chain,
  }
}

export const USERS = {
  ash: { id: 1, username: 'ash', email: 'ash@pallet.town', role: 'USER', password: 'pikachu123' },
  oak: { id: 2, username: 'oak', email: 'oak@pallet.town', role: 'ADMIN', password: 'professor123' },
} as const
export type DemoUser = keyof typeof USERS
export const tokenOf = (user: DemoUser) => `token-${user}`

function synced(id: number, pokedexNumber: number): LocalPokemon {
  const { evolutionChain: _, ...catalog } = detail(pokedexNumber)
  return {
    ...catalog,
    id,
    pokedexNumber,
    localizedNames: { ja: 'ピカチュウ' },
    region: 'Kanto',
    habitat: 'forest',
    coordinates: { latitude: 35.6762, longitude: 139.6503 },
    tags: ['mascot'],
    syncedAt: '2026-10-16T01:20:31.523Z',
    version: 0,
  }
}

function missingno(id: number): LocalPokemon {
  return {
    id, pokedexNumber: null, name: 'missingno', category: 'Glitch Pokémon', description: null,
    heightM: 1, weightKg: 1590.8, spriteUrl: null, imageUrl: null, types: ['normal'],
    abilities: [{ name: 'pressure', hidden: false }],
    stats: { hp: 33, attack: 136, defense: 1, specialAttack: 6, specialDefense: 6, speed: 29, total: 211 },
    localizedNames: {}, region: null, habitat: null, coordinates: null, tags: ['glitch'], syncedAt: null, version: 0,
  }
}

interface State {
  users: (User & { password: string })[]
  local: LocalPokemon[]
  nextLocalId: number
  /** every request the fake received, for asserting what the UI sent */
  requests: { method: string; path: string; body: unknown; authorization: string | null }[]
}

export const fake: State = { users: [], local: [], nextLocalId: 1, requests: [] }

export function resetFakeApi() {
  fake.users = Object.values(USERS).map((user) => ({ ...user }))
  fake.local = [synced(1, 25), missingno(2)]
  fake.nextLocalId = 3
  fake.requests = []
}

const API = 'http://localhost:3000/api/v1'

const problem = (status: number, detail: string, extra: Partial<ProblemDetail> = {}) =>
  HttpResponse.json({ type: 'about:blank', title: titleOf(status), status, detail, ...extra },
    { status, headers: { 'Content-Type': 'application/problem+json' } })

function titleOf(status: number) {
  return ({ 400: 'Bad Request', 401: 'Unauthorized', 403: 'Forbidden', 404: 'Not Found', 409: 'Conflict', 503: 'Service Unavailable' } as Record<number, string>)[status] ?? 'Error'
}

function caller(request: Request) {
  const token = request.headers.get('Authorization')?.replace('Bearer ', '')
  return fake.users.find((user) => `token-${user.username}` === token) ?? null
}

/** Like the API's security rules: reads of the local store need a token, writes need ADMIN. */
function guard(request: Request, needsAdmin: boolean) {
  const user = caller(request)
  if (!user) return problem(401, 'Full authentication is required to access this resource')
  if (needsAdmin && user.role !== 'ADMIN') return problem(403, 'Access Denied')
  return null
}

function pageOf<T>(items: T[], url: URL) {
  const page = Number(url.searchParams.get('page') ?? 0)
  const size = Number(url.searchParams.get('size') ?? 20)
  const totalPages = Math.ceil(items.length / size)
  return {
    items: items.slice(page * size, page * size + size),
    page, size, totalItems: items.length, totalPages,
    hasNext: page + 1 < totalPages, hasPrevious: page > 0,
  }
}

/** Like the API's NameQuery: blank is no search; text and its slug spelling ("Mr. Mime" → mr-mime); 1–50 characters. */
function searchOf(raw: string | null) {
  const text = raw?.trim().toLowerCase() ?? ''
  if (!text) return null
  if (text.length > 50) return problem(400, 'Search text must be at most 50 characters', { title: 'Invalid search' })
  if (!/[\p{L}\p{N}]/u.test(text)) return problem(400, 'Search text must contain a letter or a digit', { title: 'Invalid search' })
  const slug = text.replace(/['’]/g, '').replace(/^[\s.]+|[\s.]+$/g, '').replace(/[\s.]+/g, '-').replace(/-{2,}/g, '-')
  return { text, slug }
}

const CATALOG_FIELDS = ['name', 'category', 'description', 'heightM', 'weightKg', 'spriteUrl', 'imageUrl', 'types', 'abilities', 'stats']

function withTotal(stats: Omit<BaseStats, 'total'>): BaseStats {
  return { ...stats, total: Object.values(stats).reduce((sum, value) => sum + value, 0) }
}

async function record(request: Request) {
  const body = request.method === 'GET' || request.method === 'DELETE' ? undefined : await request.clone().json().catch(() => undefined)
  fake.requests.push({
    method: request.method,
    path: new URL(request.url).pathname.replace('/api/v1', '') + new URL(request.url).search,
    body,
    authorization: request.headers.get('Authorization'),
  })
  return body as Record<string, unknown> | undefined
}

export const handlers = [
  http.get(`${API}/pokemon`, async ({ request }) => {
    await record(request)
    const url = new URL(request.url)
    const search = searchOf(url.searchParams.get('name'))
    if (search instanceof Response) return search
    const ids = Array.from({ length: CATALOG_SIZE }, (_, i) => i + 1)
    const matches = search ? ids.filter((id) => nameOf(id).includes(search.slug)) : ids
    return HttpResponse.json(pageOf(matches.map(summary), url))
  }),

  http.get(`${API}/pokemon/:id`, async ({ request, params }) => {
    await record(request)
    const id = Number(params.id)
    if (!(id >= 1)) return problem(400, 'Invalid Pokémon id')
    if (id > CATALOG_SIZE) return problem(404, `Pokemon ${id} not found`)
    return HttpResponse.json(detail(id))
  }),

  http.post(`${API}/auth/login`, async ({ request }) => {
    const body = await record(request) as { login: string; password: string }
    const user = fake.users.find((u) => [u.username, u.email].some((v) => v.toLowerCase() === body.login.toLowerCase()))
    if (!user || user.password !== body.password) return problem(401, 'Invalid username, email or password')
    return HttpResponse.json({ accessToken: `token-${user.username}`, tokenType: 'Bearer', expiresIn: 3600 })
  }),

  http.post(`${API}/auth/register`, async ({ request }) => {
    const body = await record(request) as { username: string; email: string; password: string }
    if (body.password.length < 8) return problem(400, 'Password must be 8 to 72 bytes', { title: 'Invalid user data', field: 'password' })
    if (fake.users.some((u) => u.username.toLowerCase() === body.username.toLowerCase())) {
      return problem(409, `Username '${body.username}' is already taken`, { title: 'Username taken' })
    }
    const user = { id: fake.users.length + 1, ...body, role: 'USER' as const }
    fake.users.push(user)
    const { password: _, ...visible } = user
    return HttpResponse.json(visible, { status: 201 })
  }),

  http.get(`${API}/auth/me`, async ({ request }) => {
    await record(request)
    const user = caller(request)
    if (!user) return problem(401, 'Full authentication is required to access this resource')
    const { password: _, ...visible } = user
    return HttpResponse.json(visible)
  }),

  http.get(`${API}/local-pokemon`, async ({ request }) => {
    await record(request)
    const denied = guard(request, false)
    if (denied) return denied
    const url = new URL(request.url)
    const search = searchOf(url.searchParams.get('q'))
    if (search instanceof Response) return search
    const matches = fake.local.filter((p) => !search || p.name.includes(search.slug)
      || Object.values(p.localizedNames).some((name) => name.toLowerCase().includes(search.text)))
    const ordered = matches.sort((a, b) => (a.pokedexNumber ?? Infinity) - (b.pokedexNumber ?? Infinity))
    return HttpResponse.json(pageOf(ordered, url))
  }),

  http.get(`${API}/local-pokemon/:id`, async ({ request, params }) => {
    await record(request)
    const denied = guard(request, false)
    if (denied) return denied
    const found = fake.local.find((p) => p.id === Number(params.id))
    return found ? HttpResponse.json(found) : problem(404, `Local Pokémon ${params.id} not found`)
  }),

  http.post(`${API}/local-pokemon/sync/:number`, async ({ request, params }) => {
    await record(request)
    const denied = guard(request, true)
    if (denied) return denied
    const number = Number(params.number)
    if (number > CATALOG_SIZE) return problem(404, `Pokemon ${number} not found`)
    const existing = fake.local.find((p) => p.pokedexNumber === number)
    if (existing) {
      Object.assign(existing, { syncedAt: new Date().toISOString(), version: existing.version + 1 })
      return HttpResponse.json(existing)
    }
    const created = { ...synced(fake.nextLocalId++, number), localizedNames: {}, region: null, habitat: null, coordinates: null, tags: [] }
    fake.local.push(created)
    return HttpResponse.json(created, { status: 201, headers: { Location: `/api/v1/local-pokemon/${created.id}` } })
  }),

  http.post(`${API}/local-pokemon`, async ({ request }) => {
    const body = await record(request) as Record<string, unknown>
    const denied = guard(request, true)
    if (denied) return denied
    if (fake.local.some((p) => p.name === body.name)) return problem(409, `A local Pokémon named '${body.name}' already exists`, { title: 'Name taken' })
    const created = {
      ...missingno(fake.nextLocalId++), ...body, stats: withTotal(body.stats as Omit<BaseStats, 'total'>),
    } as LocalPokemon
    fake.local.push(created)
    return HttpResponse.json(created, { status: 201 })
  }),

  http.put(`${API}/local-pokemon/:id`, async ({ request, params }) => {
    const body = await record(request) as Record<string, unknown>
    const denied = guard(request, true)
    if (denied) return denied
    const found = fake.local.find((p) => p.id === Number(params.id))
    if (!found) return problem(404, `Local Pokémon ${params.id} not found`)
    if (body.version !== found.version) {
      return problem(409, 'The Pokémon was changed by another request meanwhile. Reload it and try again.', { title: 'Concurrent update' })
    }
    const catalogField = CATALOG_FIELDS.find((field) => field in body)
    if (found.pokedexNumber !== null && catalogField) {
      return problem(400, 'Catalog fields not editable: a synced Pokémon carries only proprietary fields', { field: catalogField })
    }
    const { version: _, ...fields } = body
    Object.assign(found, { localizedNames: {}, region: null, habitat: null, coordinates: null, tags: [] }, fields)
    if (fields.stats) found.stats = withTotal(fields.stats as Omit<BaseStats, 'total'>)
    found.version += 1
    return HttpResponse.json(found)
  }),

  http.delete(`${API}/local-pokemon/:id`, async ({ request, params }) => {
    await record(request)
    const denied = guard(request, true)
    if (denied) return denied
    const index = fake.local.findIndex((p) => p.id === Number(params.id))
    if (index < 0) return problem(404, `Local Pokémon ${params.id} not found`)
    fake.local.splice(index, 1)
    return new HttpResponse(null, { status: 204 })
  }),
]

export const server = setupServer(...handlers)

/** Makes one endpoint answer with a problem (for the rest of the test, or `once`), e.g. PokeAPI being down. */
export function failWith(
  method: 'get' | 'post' | 'put' | 'delete', path: string, status: number, detail: string,
  extra: Partial<ProblemDetail> = {}, { once = false } = {},
) {
  server.use(http[method](`${API}${path}`, () => problem(status, detail, extra), { once }))
}
