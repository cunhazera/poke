import { Link, useParams } from 'react-router'
import { isNotFound } from '../api/client'
import { useSyncPokemon } from '../api/localPokemon'
import { usePokemonDetail } from '../api/pokemon'
import type { PokemonDetail } from '../api/types'
import { useSession } from '../auth/session'
import { AbilityList, EvolutionTree, PokemonImage, StatBars, TypeList } from '../components/Pokemon'
import { ErrorPanel, Loading, NotFound } from '../components/Status'
import { SyncNotice } from '../components/SyncNotice'
import { dexNumber, displayName, kilograms, metres, parseId } from '../format'
import styles from './DetailPage.module.css'

/** US02: everything about one species. Admins can copy it into the local store from here (US03). */
export function PokemonDetailPage() {
  const id = parseId(useParams().id)
  return id === null ? <PokemonNotFound /> : <PokemonDetailView key={id} id={id} />
}

function PokemonNotFound() {
  return <NotFound title="Pokémon not found"><p className="muted">No species has that Pokédex number.</p></NotFound>
}

function PokemonDetailView({ id }: { id: number }) {
  const { data: pokemon, error, isPending, refetch } = usePokemonDetail(id)

  if (isPending) return <Loading label="Loading Pokémon…" />
  if (isNotFound(error)) return <PokemonNotFound />
  if (error) return <ErrorPanel error={error} onRetry={() => refetch()} />

  const name = displayName(pokemon.name)
  return (
    <article className={styles.page}>
      <nav aria-label="Browse" className={styles.browse}>
        {id > 1 ? <Link to={`/pokemon/${id - 1}`} aria-label="Previous Pokémon">← {dexNumber(id - 1)}</Link> : <span />}
        <Link to="/">All Pokémon</Link>
        <Link to={`/pokemon/${id + 1}`} aria-label="Next Pokémon">{dexNumber(id + 1)} →</Link>
      </nav>

      <section className={`card ${styles.hero}`}>
        <PokemonImage src={pokemon.imageUrl ?? pokemon.spriteUrl} alt={`${name} artwork`} size={280} />
        <div className={styles.summary}>
          <span className={styles.number}>{dexNumber(pokemon.id)}</span>
          <h1>{name}</h1>
          {pokemon.category && <p className={styles.category}>{pokemon.category}</p>}
          <TypeList types={pokemon.types} />
          {pokemon.description && <p className={styles.description}>{pokemon.description}</p>}
          <dl className={styles.facts}>
            <div><dt>Height</dt><dd>{metres(pokemon.heightM)}</dd></div>
            <div><dt>Weight</dt><dd>{kilograms(pokemon.weightKg)}</dd></div>
          </dl>
          <div>
            <h2 className={styles.label}>Abilities</h2>
            <AbilityList abilities={pokemon.abilities} />
          </div>
          <AdminSync pokemon={pokemon} />
        </div>
      </section>

      <div className={styles.columns}>
        <section className={`card ${styles.section}`}>
          <h2>Base stats</h2>
          <StatBars stats={pokemon.stats} />
        </section>
        <section className={`card ${styles.section}`}>
          <h2>Evolution</h2>
          <EvolutionTree root={pokemon.evolutionChain} currentId={pokemon.id} />
        </section>
      </div>
    </article>
  )
}

function AdminSync({ pokemon }: { pokemon: PokemonDetail }) {
  const { isAdmin } = useSession()
  const sync = useSyncPokemon()
  if (!isAdmin) return null
  return (
    <div className={styles.actions}>
      <button type="button" className="button primary" disabled={sync.isPending} onClick={() => sync.mutate(pokemon.id)}>
        {sync.isPending ? 'Syncing…' : 'Sync to local store'}
      </button>
      {sync.data && <SyncNotice outcome={sync.data} />}
      {sync.error && <p role="alert" className="notice error">{sync.error.message}</p>}
    </div>
  )
}
