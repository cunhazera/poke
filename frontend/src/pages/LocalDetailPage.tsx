import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { isNotFound } from '../api/client'
import { useDeleteLocalPokemon, useLocalPokemon, useSyncPokemon } from '../api/localPokemon'
import type { LocalPokemon } from '../api/types'
import { useSession } from '../auth/session'
import { AbilityList, PokemonImage, StatBars, TypeList } from '../components/Pokemon'
import { ErrorPanel, Loading, NotFound } from '../components/Status'
import { SyncNotice } from '../components/SyncNotice'
import { dateTime, dexNumber, displayName, kilograms, metres, parseId } from '../format'
import styles from './DetailPage.module.css'

function LocalNotFound() {
  return <NotFound title="Pokémon not found"><p className="muted">The local store has no Pokémon with that id.</p></NotFound>
}

/** US03/US04: one local copy, catalog and proprietary data side by side; admins edit, re-sync or delete it. */
export function LocalDetailPage() {
  const id = parseId(useParams().id)
  return id === null ? <LocalNotFound /> : <LocalDetailView key={id} id={id} />
}

/** The list this page was opened from (keeping its page and search), else the whole store. */
function useBackToList() {
  const from = (useLocation().state as { from?: string } | null)?.from
  return from?.startsWith('/local') ? from : '/local'
}

function LocalDetailView({ id }: { id: number }) {
  const { data: pokemon, error, isPending, refetch } = useLocalPokemon(id)
  const backTo = useBackToList()

  if (isPending) return <Loading />
  if (isNotFound(error)) return <LocalNotFound />
  if (error) return <ErrorPanel error={error} onRetry={() => refetch()} />

  const name = displayName(pokemon.name)
  return (
    <article className={styles.page}>
      <nav aria-label="Breadcrumb" className={styles.browse}>
        <Link to={backTo}>← Local store</Link>
      </nav>

      <section className={`card ${styles.hero}`}>
        <PokemonImage src={pokemon.imageUrl ?? pokemon.spriteUrl} alt={`${name} artwork`} size={240} />
        <div className={styles.summary}>
          <span className={styles.number}>
            {pokemon.pokedexNumber !== null ? dexNumber(pokemon.pokedexNumber) : 'Created locally'}
          </span>
          <h1>{name}</h1>
          {pokemon.category && <p className={styles.category}>{pokemon.category}</p>}
          <TypeList types={pokemon.types} />
          {pokemon.description && <p className={styles.description}>{pokemon.description}</p>}
          <dl className={styles.facts}>
            <div><dt>Height</dt><dd>{metres(pokemon.heightM)}</dd></div>
            <div><dt>Weight</dt><dd>{kilograms(pokemon.weightKg)}</dd></div>
          </dl>
          <AbilityList abilities={pokemon.abilities} />
          {pokemon.pokedexNumber !== null && (
            <Link to={`/pokemon/${pokemon.pokedexNumber}`}>View in Pokédex</Link>
          )}
          <AdminActions pokemon={pokemon} />
        </div>
      </section>

      <div className={styles.columns}>
        <ProprietaryData pokemon={pokemon} />
        <section className={`card ${styles.section}`}>
          <h2>Base stats</h2>
          <StatBars stats={pokemon.stats} />
        </section>
      </div>
    </article>
  )
}

const NOT_SET = <span className="muted">Not set</span>

function ProprietaryData({ pokemon }: { pokemon: LocalPokemon }) {
  const names = Object.entries(pokemon.localizedNames)
  return (
    <section className={`card ${styles.section}`}>
      <h2>Proprietary data</h2>
      <dl className={styles.pairs}>
        <dt>Localized names</dt>
        <dd>
          {names.length === 0 ? NOT_SET : (
            <ul className="chips">
              {names.map(([language, localized]) => (
                <li key={language} className="chip" lang={language}><abbr className="muted">{language}</abbr> {localized}</li>
              ))}
            </ul>
          )}
        </dd>
        <dt>Region</dt>
        <dd>{pokemon.region ?? NOT_SET}</dd>
        <dt>Habitat</dt>
        <dd>{pokemon.habitat ?? NOT_SET}</dd>
        <dt>Coordinates</dt>
        <dd>{pokemon.coordinates ? `${pokemon.coordinates.latitude}, ${pokemon.coordinates.longitude}` : NOT_SET}</dd>
        <dt>Tags</dt>
        <dd>
          {pokemon.tags.length === 0 ? NOT_SET : (
            <ul className="chips">{pokemon.tags.map((tag) => <li key={tag} className="chip">{tag}</li>)}</ul>
          )}
        </dd>
        <dt>Synced</dt>
        <dd>{pokemon.syncedAt ? dateTime(pokemon.syncedAt) : <span className="muted">Never (created locally)</span>}</dd>
        <dt>Version</dt>
        <dd>{pokemon.version}</dd>
      </dl>
    </section>
  )
}

function AdminActions({ pokemon }: { pokemon: LocalPokemon }) {
  const { isAdmin } = useSession()
  const navigate = useNavigate()
  const sync = useSyncPokemon()
  const remove = useDeleteLocalPokemon()
  const [confirming, setConfirming] = useState(false)
  if (!isAdmin) return null

  const name = displayName(pokemon.name)
  const confirmId = `delete-${pokemon.id}`
  return (
    <div className={styles.actions}>
      <div className={styles.buttons}>
        <Link to={`/local/${pokemon.id}/edit`} className="button primary">Edit</Link>
        {pokemon.pokedexNumber !== null && (
          <button type="button" className="button" disabled={sync.isPending} onClick={() => sync.mutate(pokemon.pokedexNumber!)}>
            {sync.isPending ? 'Syncing…' : 'Re-sync from PokeAPI'}
          </button>
        )}
        <button type="button" className="button danger" onClick={() => setConfirming(true)} disabled={confirming}>Delete</button>
      </div>
      {sync.data && <SyncNotice outcome={sync.data} linkToCopy={false} />}
      {sync.error && <p role="alert" className="notice error">{sync.error.message}</p>}
      {confirming && (
        <div role="alertdialog" aria-labelledby={confirmId} className={`card ${styles.confirm}`}>
          <strong id={confirmId}>Delete {name} from the local store?</strong>
          <span>
            {pokemon.pokedexNumber !== null
              ? 'Its proprietary data is lost; the catalog data can be synced again.'
              : 'It exists only here, so it is gone for good.'}
          </span>
          {remove.error && <span className="notice error">{remove.error.message}</span>}
          <div className={styles.buttons}>
            <button
              type="button"
              className="button danger solid"
              disabled={remove.isPending}
              onClick={() => remove.mutate(pokemon.id, { onSuccess: () => navigate('/local', { replace: true }) })}
            >
              {remove.isPending ? 'Deleting…' : 'Delete permanently'}
            </button>
            <button type="button" className="button" onClick={() => setConfirming(false)}>Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}
