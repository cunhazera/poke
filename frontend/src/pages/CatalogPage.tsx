import { Link, useSearchParams } from 'react-router'
import { usePokemonPage } from '../api/pokemon'
import type { PokemonSummary } from '../api/types'
import { Pagination } from '../components/Pagination'
import { AbilityList, PokemonImage } from '../components/Pokemon'
import { ErrorPanel, Loading } from '../components/Status'
import { dexNumber, displayName, kilograms, pageFromSearch } from '../format'
import styles from './CatalogPage.module.css'

const PAGE_SIZE = 20

/** US01: the paginated Pokédex. The page lives in the URL (`?page=2`), so back/forward and links work. */
export function CatalogPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const page = pageFromSearch(searchParams.get('page'))
  const { data, error, isPending, isPlaceholderData, refetch } = usePokemonPage(page, PAGE_SIZE)

  const goTo = (next: number) => {
    setSearchParams(next === 0 ? {} : { page: String(next + 1) })
    window.scrollTo?.({ top: 0 })
  }

  return (
    <>
      <div className={styles.intro}>
        <h1>Pokédex</h1>
        {data && <p className="muted">{data.totalItems} species, in Pokédex order.</p>}
      </div>
      {isPending ? (
        <Loading label="Loading Pokémon…" />
      ) : !data ? (
        <ErrorPanel error={error} onRetry={() => refetch()} />
      ) : data.items.length === 0 ? (
        <div className="card" style={{ padding: '1.5rem', textAlign: 'center' }}>
          <p>There are no Pokémon on this page.</p>
          <Link to="/">Go to the first page</Link>
        </div>
      ) : (
        <>
          <ul className={styles.grid} aria-busy={isPlaceholderData} data-stale={isPlaceholderData || undefined}>
            {data.items.map((pokemon) => <li key={pokemon.id}><PokemonCard pokemon={pokemon} /></li>)}
          </ul>
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            hasPrevious={data.hasPrevious}
            hasNext={data.hasNext}
            onChange={goTo}
            busy={isPlaceholderData}
          />
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
