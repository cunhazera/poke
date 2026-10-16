import { type FormEvent, useState } from 'react'
import { Link, useLocation } from 'react-router'
import { ApiError } from '../api/client'
import { useLocalPokemonPage, useSyncPokemon } from '../api/localPokemon'
import type { LocalPokemon } from '../api/types'
import { useSession } from '../auth/session'
import { Field } from '../components/Field'
import { Pagination } from '../components/Pagination'
import { PokemonImage, TypeList } from '../components/Pokemon'
import { ErrorPanel, Loading } from '../components/Status'
import { SearchBox } from '../components/SearchBox'
import { SyncNotice } from '../components/SyncNotice'
import { dexNumber, displayName } from '../format'
import { matchesText, useListParams } from '../searchParams'
import styles from './LocalListPage.module.css'

const PAGE_SIZE = 20
const MAX_POKEDEX_NUMBER = 1025

/** US03: our own copies, with the proprietary data, searchable by name or localized name; admins sync and create here. */
export function LocalListPage() {
  const { isAdmin } = useSession()
  const { page, search, setPage, setSearch } = useListParams('q')
  const { data, error, isPending, isPlaceholderData, refetch } = useLocalPokemonPage(page, PAGE_SIZE, search)
  const rejected = error instanceof ApiError && error.status === 400

  return (
    <>
      <div className={styles.header}>
        <div>
          <h1>Local store</h1>
          <p className="muted">Our copies of Pokémon, with data PokeAPI doesn't have: localized names, geography and tags.</p>
        </div>
        {isAdmin && <Link to="/local/new" className="button primary">New Pokémon</Link>}
      </div>
      {isAdmin && <SyncForm />}
      <div className={styles.searchRow}>
        <SearchBox
          label="Search the local store"
          placeholder="Search by name or localized name"
          value={search}
          onSearch={setSearch}
        />
        {search && data && !isPlaceholderData && <span className="muted">{matchesText(data.totalItems, search)}</span>}
      </div>
      {rejected ? (
        <p role="alert" className="notice error">{error.message}</p>
      ) : isPending ? (
        <Loading label="Loading the local store…" />
      ) : !data ? (
        <ErrorPanel error={error} onRetry={() => refetch()} />
      ) : data.items.length === 0 ? (
        <div className={`card ${styles.empty}`}>
          {search ? (
            <>
              <p>No Pokémon in the local store match “{search}”.</p>
              <button type="button" className="button" onClick={() => setSearch('')}>Clear search</button>
            </>
          ) : (
            <>
              <p>{data.totalItems === 0 ? 'The local store is empty.' : 'There are no Pokémon on this page.'}</p>
              {data.totalItems > 0 && <Link to="/local">Go to the first page</Link>}
            </>
          )}
        </div>
      ) : (
        <>
          <ul className={styles.list} data-stale={isPlaceholderData || undefined}>
            {data.items.map((pokemon) => <li key={pokemon.id}><LocalPokemonRow pokemon={pokemon} /></li>)}
          </ul>
          {data.totalPages > 1 && (
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              hasPrevious={data.hasPrevious}
              hasNext={data.hasNext}
              onChange={setPage}
              busy={isPlaceholderData}
            />
          )}
        </>
      )}
    </>
  )
}

function LocalPokemonRow({ pokemon }: { pokemon: LocalPokemon }) {
  const name = displayName(pokemon.name)
  // The detail page's "back" link returns to this exact list: same page, same search.
  const location = useLocation()
  const listUrl = location.pathname + location.search
  return (
    <article className={`card ${styles.row}`}>
      <PokemonImage src={pokemon.spriteUrl} alt="" size={72} />
      <div className={styles.main}>
        <div className={styles.title}>
          <h2><Link to={`/local/${pokemon.id}`} state={{ from: listUrl }}>{name}</Link></h2>
          {pokemon.pokedexNumber !== null ? (
            <>
              <span className="muted">{dexNumber(pokemon.pokedexNumber)}</span>
              <span className={`${styles.badge} ${styles.synced}`}>Synced</span>
            </>
          ) : (
            <span className={`${styles.badge} ${styles.local}`}>Local only</span>
          )}
        </div>
        <TypeList types={pokemon.types} />
      </div>
      <div className={styles.meta}>
        {pokemon.region && <span className={styles.region}>{pokemon.region}</span>}
        {pokemon.tags.length > 0 && (
          <ul className="chips" aria-label="Tags">
            {pokemon.tags.map((tag) => <li key={tag} className="chip">{tag}</li>)}
          </ul>
        )}
      </div>
    </article>
  )
}

/** US03: copy a Pokémon from PokeAPI by its number. A re-sync refreshes the catalog part only. */
function SyncForm() {
  const sync = useSyncPokemon()
  const [number, setNumber] = useState('')
  const [invalid, setInvalid] = useState(false)

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const value = Number(number)
    if (!Number.isInteger(value) || value < 1 || value > MAX_POKEDEX_NUMBER) {
      setInvalid(true)
      return
    }
    setInvalid(false)
    sync.mutate(value, { onSuccess: () => setNumber('') })
  }

  return (
    <form className={`card ${styles.sync}`} onSubmit={submit} aria-label="Sync from PokeAPI" noValidate>
      <div className={styles.syncRow}>
        <Field
          label="Pokédex number"
          error={invalid ? `Enter a Pokédex number from 1 to ${MAX_POKEDEX_NUMBER}.` : sync.error?.message}
          hint="Copies the Pokémon from PokeAPI, or refreshes the copy we have."
        >
          <input type="number" inputMode="numeric" min={1} max={MAX_POKEDEX_NUMBER} value={number} onChange={(e) => setNumber(e.target.value)} />
        </Field>
        <button type="submit" className="button primary" disabled={sync.isPending}>
          {sync.isPending ? 'Syncing…' : 'Sync'}
        </button>
      </div>
      {sync.data && !sync.isPending && <SyncNotice outcome={sync.data} />}
    </form>
  )
}
