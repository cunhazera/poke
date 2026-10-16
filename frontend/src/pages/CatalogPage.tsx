import { Link, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { usePokemonPage } from '../api/pokemon'
import type { PokemonSummary } from '../api/types'
import { Pagination } from '../components/Pagination'
import { AbilityList, PokemonImage } from '../components/Pokemon'
import { SearchBox } from '../components/SearchBox'
import { ErrorPanel, Loading } from '../components/Status'
import { dexNumber, displayName, kilograms } from '../format'
import { matchesText, useListParams } from '../searchParams'
import styles from './CatalogPage.module.css'

const PAGE_SIZE = 20

/**
 * US01: the paginated Pokédex, searchable by name. The page and the search live in the URL (`?name=char&page=2`),
 * so back/forward and links work.
 */
export function CatalogPage() {
  const navigate = useNavigate()
  const { page, search, setPage, setSearch } = useListParams('name')
  const { data, error, isPending, isPlaceholderData, refetch } = usePokemonPage(page, PAGE_SIZE, search)

  const goTo = (next: number) => {
    setPage(next)
    window.scrollTo?.({ top: 0 })
  }
  /** A number is a Pokédex number: open that Pokémon instead of searching names. */
  const jumpToNumber = (text: string) => {
    if (!/^[0-9]{1,4}$/.test(text) || Number(text) < 1) return false
    navigate(`/pokemon/${Number(text)}`)
    return true
  }
  const rejected = error instanceof ApiError && error.status === 400

  return (
    <>
      <div className={styles.intro}>
        <div>
          <h1>Pokédex</h1>
          {data && !isPlaceholderData && (
            <p className="muted">{search ? matchesText(data.totalItems, search) : `${data.totalItems} species, in Pokédex order.`}</p>
          )}
        </div>
        <SearchBox
          label="Search the Pokédex"
          placeholder="Search by name or number"
          value={search}
          onSearch={setSearch}
          onSubmit={jumpToNumber}
        />
      </div>
      {rejected ? (
        <p role="alert" className="notice error">{error.message}</p>
      ) : isPending ? (
        <Loading label="Loading Pokémon…" />
      ) : !data ? (
        <ErrorPanel error={error} onRetry={() => refetch()} />
      ) : data.items.length === 0 ? (
        <div className={`card ${styles.empty}`}>
          {search ? (
            <>
              <p>No Pokémon match “{search}”.</p>
              <button type="button" className="button" onClick={() => setSearch('')}>Clear search</button>
            </>
          ) : (
            <>
              <p>There are no Pokémon on this page.</p>
              <Link to="/">Go to the first page</Link>
            </>
          )}
        </div>
      ) : (
        <>
          <ul className={styles.grid} aria-busy={isPlaceholderData} data-stale={isPlaceholderData || undefined}>
            {data.items.map((pokemon) => <li key={pokemon.id}><PokemonCard pokemon={pokemon} /></li>)}
          </ul>
          {data.totalPages > 1 && (
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              hasPrevious={data.hasPrevious}
              hasNext={data.hasNext}
              onChange={goTo}
              busy={isPlaceholderData}
            />
          )}
        </>
      )}
    </>
  )
}

function PokemonCard({ pokemon }: { pokemon: PokemonSummary }) {
  const name = displayName(pokemon.name)
  return (
    <article className={`card ${styles.card}`}>
      <Link to={`/pokemon/${pokemon.id}`} className={styles.link}>
        <PokemonImage src={pokemon.spriteUrl} alt={name} size={96} />
        <span className={styles.number}>{dexNumber(pokemon.id)}</span>
        <h2 className={styles.name}>{name}</h2>
      </Link>
      <dl className={styles.facts}>
        <div><dt>Category</dt><dd>{pokemon.category ?? 'Unknown'}</dd></div>
        <div><dt>Weight</dt><dd>{kilograms(pokemon.weightKg)}</dd></div>
      </dl>
      <AbilityList abilities={pokemon.abilities} />
    </article>
  )
}
