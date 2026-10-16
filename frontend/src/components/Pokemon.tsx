import { Link } from 'react-router'
import type { Ability, BaseStats, EvolutionStage } from '../api/types'
import { STAT_NAMES, displayName, statLabel } from '../format'
import styles from './Pokemon.module.css'

const TYPE_COLOURS: Record<string, string> = {
  normal: '#9fa19f', fire: '#e62829', water: '#2980ef', electric: '#e5b800', grass: '#3fa129', ice: '#3dcef3',
  fighting: '#ff8000', poison: '#9141cb', ground: '#915121', flying: '#81b9ef', psychic: '#ef4179', bug: '#91a119',
  rock: '#afa981', ghost: '#704170', dragon: '#5060e1', dark: '#624d4e', steel: '#60a1b8', fairy: '#ef70ef',
}

export function TypeList({ types }: { types: string[] }) {
  return (
    <ul className={styles.types} aria-label="Types">
      {types.map((type) => (
        <li key={type} className={styles.type} style={{ background: TYPE_COLOURS[type] ?? 'var(--ink-soft)' }}>
          {displayName(type)}
        </li>
      ))}
    </ul>
  )
}

export function AbilityList({ abilities }: { abilities: Ability[] }) {
  return (
    <ul className="chips" aria-label="Abilities">
      {abilities.map((ability) => (
        <li key={ability.name} className={`chip ${ability.hidden ? styles.hidden : ''}`} title={ability.hidden ? 'Hidden ability' : undefined}>
          {displayName(ability.name)}
        </li>
      ))}
    </ul>
  )
}

/** A sprite or artwork, with a neutral placeholder when PokeAPI has none (or for local-only Pokémon). */
export function PokemonImage({ src, alt, size }: { src: string | null; alt: string; size: number }) {
  if (!src) {
    return <div role="img" aria-label={alt} className={styles.placeholder} style={{ width: size, height: size }}>?</div>
  }
  return <img src={src} alt={alt} width={size} height={size} loading="lazy" className={styles.image} />
}

/** Base stats as meters on the 1–255 scale every stat shares. */
export function StatBars({ stats }: { stats: BaseStats }) {
  return (
    <dl className={styles.stats}>
      {STAT_NAMES.map((stat) => (
        <div key={stat} className={styles.stat}>
          <dt>{statLabel(stat)}</dt>
          <dd>
            <span className={styles.statValue}>{stats[stat]}</span>
            <meter aria-label={statLabel(stat)} min={1} max={255} low={60} high={100} optimum={150} value={stats[stat]} />
          </dd>
        </div>
      ))}
      <div className={`${styles.stat} ${styles.total}`}>
        <dt>Total</dt>
        <dd><span className={styles.statValue}>{stats.total}</span></dd>
      </div>
    </dl>
  )
}

/** The chain as a tree: each stage links to its page, branches (Eevee) sit side by side. */
export function EvolutionTree({ root, currentId }: { root: EvolutionStage; currentId: number }) {
  return (
    <ol className={styles.tree}>
      <EvolutionNode stage={root} currentId={currentId} />
    </ol>
  )
}

function EvolutionNode({ stage, currentId }: { stage: EvolutionStage; currentId: number }) {
  const name = displayName(stage.name)
  const content = (
    <>
      <PokemonImage src={stage.spriteUrl} alt="" size={72} />
      <span>{name}</span>
    </>
  )
  return (
    <li className={styles.node}>
      {stage.id === currentId ? (
        <span className={`${styles.stage} ${styles.current}`} aria-current="page">{content}</span>
      ) : (
        <Link to={`/pokemon/${stage.id}`} className={styles.stage} aria-label={name}>{content}</Link>
      )}
      {stage.evolvesTo.length > 0 && (
        <ol className={`${styles.branches} ${stage.evolvesTo.length > 3 ? styles.many : ''}`}>
          {stage.evolvesTo.map((next) => <EvolutionNode key={next.id} stage={next} currentId={currentId} />)}
        </ol>
      )}
    </li>
  )
}
