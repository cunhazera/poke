import { Link } from 'react-router'
import type { SyncOutcome } from '../api/localPokemon'
import { displayName } from '../format'

/** What a sync did: a first copy (with a link to it) or a refresh that kept our proprietary data. */
export function SyncNotice({ outcome, linkToCopy = true }: { outcome: SyncOutcome; linkToCopy?: boolean }) {
  const name = displayName(outcome.pokemon.name)
  return (
    <div role="status" className="notice">
      <span>
        {outcome.created
          ? `${name} was added to the local store.`
          : `${name} was refreshed from PokeAPI; its proprietary data was kept.`}
      </span>
      {linkToCopy && <Link to={`/local/${outcome.pokemon.id}`}>View local copy</Link>}
    </div>
  )
}
