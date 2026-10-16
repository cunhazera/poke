import { type FormEvent, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, isNotFound } from '../api/client'
import { useCreateLocalPokemon, useLocalPokemon, useUpdateLocalPokemon } from '../api/localPokemon'
import { type LocalPokemon, POKEMON_TYPES } from '../api/types'
import { Field } from '../components/Field'
import { ErrorPanel, Loading, NotFound } from '../components/Status'
import { STAT_NAMES, displayName, parseId, statLabel } from '../format'
import styles from './LocalFormPage.module.css'
import {
  type FormValues, checkValues, emptyValues, errorAt, MAX_ABILITIES, toCreateRequest, toUpdateRequest, valuesOf,
} from './localPokemonForm'

/** US04: `/local/new` creates a local-only Pokémon; `/local/:id/edit` edits one. */
export function LocalFormPage() {
  const { id: rawId } = useParams()
  if (rawId === undefined) return <CreateForm />
  const id = parseId(rawId)
  return id === null ? <NotFound title="Pokémon not found" /> : <EditLoader key={id} id={id} />
}

function CreateForm() {
  const navigate = useNavigate()
  const create = useCreateLocalPokemon()
  return (
    <PokemonForm
      title="New Pokémon"
      intro="A Pokémon that exists only in our store. It has no Pokédex number and is never synced."
      initial={emptyValues()}
      editCatalog
      submitLabel="Create Pokémon"
      pending={create.isPending}
      error={create.error}
      cancelTo="/local"
      onSubmit={(values) => create.mutate(toCreateRequest(values), { onSuccess: (created) => navigate(`/local/${created.id}`) })}
    />
  )
}

function EditLoader({ id }: { id: number }) {
  const { data, error, isPending, refetch } = useLocalPokemon(id)
  if (isPending) return <Loading />
  if (isNotFound(error)) return <NotFound title="Pokémon not found" />
  if (error) return <ErrorPanel error={error} onRetry={() => refetch()} />
  // Re-keyed by version: loading a newer version (after a conflict) starts the form from it.
  return <EditForm key={data.version} pokemon={data} onReload={() => refetch()} />
}

function EditForm({ pokemon, onReload }: { pokemon: LocalPokemon; onReload: () => void }) {
  const navigate = useNavigate()
  const update = useUpdateLocalPokemon(pokemon.id)
  const synced = pokemon.pokedexNumber !== null
  const stale = update.error instanceof ApiError && update.error.problem.title === 'Concurrent update'

  return (
    <PokemonForm
      title={`Edit ${displayName(pokemon.name)}`}
      intro={synced
        ? 'The catalog fields come from PokeAPI and are refreshed by every sync, so only our proprietary data is editable here.'
        : 'This Pokémon exists only in our store, so every field is editable.'}
      initial={valuesOf(pokemon)}
      editCatalog={!synced}
      submitLabel="Save changes"
      pending={update.isPending}
      error={stale ? null : update.error}
      conflict={stale ? onReload : undefined}
      cancelTo={`/local/${pokemon.id}`}
      onSubmit={(values) => update.mutate(toUpdateRequest(values, pokemon), { onSuccess: () => navigate(`/local/${pokemon.id}`) })}
    />
  )
}

/** Server errors that concern a field shown in the form; anything else is reported at the top. */
const FIELD_PATHS = [
  'name', 'category', 'description', 'heightM', 'weightKg', 'spriteUrl', 'imageUrl', 'types', 'abilities', 'stats',
  'localizedNames', 'region', 'habitat', 'coordinates', 'tags',
]

interface FormProps {
  title: string
  intro: string
  initial: FormValues
  editCatalog: boolean
  submitLabel: string
  pending: boolean
  error: Error | null
  /** set when the save hit a newer version: offers to load it */
  conflict?: () => void
  cancelTo: string
  onSubmit: (values: FormValues) => void
}

function PokemonForm({ title, intro, initial, editCatalog, submitLabel, pending, error, conflict, cancelTo, onSubmit }: FormProps) {
  const [values, setValues] = useState(initial)
  const [clientErrors, setClientErrors] = useState<Record<string, string>>({})
  const serverErrors = error instanceof ApiError ? error.fieldErrors : {}
  const errors = { ...serverErrors, ...clientErrors }
  const placed = Object.keys(serverErrors).some((key) => errorAt({ [key]: '' }, ...FIELD_PATHS) !== undefined)

  const set = <K extends keyof FormValues>(key: K, value: FormValues[K]) => setValues((current) => ({ ...current, [key]: value }))
  const bind = (key: 'name' | 'category' | 'description' | 'heightM' | 'weightKg' | 'spriteUrl' | 'imageUrl' | 'region' | 'habitat' | 'latitude' | 'longitude' | 'tags') => ({
    value: values[key],
    onChange: (event: { target: { value: string } }) => set(key, event.target.value),
  })

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const found = checkValues(values)
    setClientErrors(found)
    if (Object.keys(found).length === 0) onSubmit(values)
  }

  return (
    <form className={styles.form} onSubmit={submit} noValidate>
      <div>
        <h1>{title}</h1>
        <p className="muted">{intro}</p>
      </div>

      {conflict && (
        <div role="alert" className="notice error">
          <span>Someone else changed this Pokémon while you were editing. Load their version, then reapply your changes.</span>
          <button type="button" className="button" onClick={conflict}>Load the latest version</button>
        </div>
      )}
      {error && (
        <p role="alert" className="notice error">
          {placed ? 'Please fix the highlighted fields.' : error.message}
        </p>
      )}

      {editCatalog && (
        <fieldset className={`card ${styles.section}`}>
          <legend>Catalog data</legend>
          <div className={styles.grid}>
            <Field label="Name" error={errorAt(errors, 'name')} hint="Lowercase, like PokeAPI: mr-mime">
              <input {...bind('name')} required autoComplete="off" />
            </Field>
            <Field label="Category" error={errorAt(errors, 'category')}>
              <input {...bind('category')} placeholder="Glitch Pokémon" />
            </Field>
            <Field label="Height (m)" error={errorAt(errors, 'heightM')}>
              <input {...bind('heightM')} type="number" inputMode="decimal" step="0.1" min="0" />
            </Field>
            <Field label="Weight (kg)" error={errorAt(errors, 'weightKg')}>
              <input {...bind('weightKg')} type="number" inputMode="decimal" step="0.1" min="0" />
            </Field>
            <Field label="Primary type" error={errorAt(errors, 'types[0]', 'types')}>
              <select value={values.primaryType} onChange={(e) => set('primaryType', e.target.value)}>
                {POKEMON_TYPES.map((type) => <option key={type} value={type}>{displayName(type)}</option>)}
              </select>
            </Field>
            <Field label="Secondary type" error={errorAt(errors, 'types[1]')}>
              <select value={values.secondaryType} onChange={(e) => set('secondaryType', e.target.value)}>
                <option value="">None</option>
                {POKEMON_TYPES.map((type) => <option key={type} value={type}>{displayName(type)}</option>)}
              </select>
            </Field>
            <Field label="Description" error={errorAt(errors, 'description')} className={styles.wide}>
              <textarea {...bind('description')} maxLength={2000} />
            </Field>
            <Field label="Sprite URL" error={errorAt(errors, 'spriteUrl')}>
              <input {...bind('spriteUrl')} type="url" placeholder="https://…" />
            </Field>
            <Field label="Artwork URL" error={errorAt(errors, 'imageUrl')}>
              <input {...bind('imageUrl')} type="url" placeholder="https://…" />
            </Field>
          </div>

          <fieldset className={styles.group}>
            <legend>Abilities</legend>
            {errorAt(errors, 'abilities') && !values.abilities.some((_, i) => errorAt(errors, `abilities[${i}]`)) && (
              <p className={styles.groupError}>{errorAt(errors, 'abilities')}</p>
            )}
            {values.abilities.slice(0, MAX_ABILITIES).map((ability, i) => (
              <div key={i} className={styles.ability}>
                <Field label={`Ability ${i + 1}`} error={errorAt(errors, `abilities[${i}]`)}>
                  <input
                    value={ability.name}
                    onChange={(e) => set('abilities', values.abilities.map((a, j) => (j === i ? { ...a, name: e.target.value } : a)))}
                    placeholder={i === 0 ? 'pressure' : 'Optional'}
                  />
                </Field>
                <label className={styles.check}>
                  <input
                    type="checkbox"
                    checked={ability.hidden}
                    onChange={(e) => set('abilities', values.abilities.map((a, j) => (j === i ? { ...a, hidden: e.target.checked } : a)))}
                    aria-label={`Ability ${i + 1} is hidden`}
                  />
                  Hidden
                </label>
              </div>
            ))}
          </fieldset>

          <fieldset className={styles.group}>
            <legend>Base stats (1–255)</legend>
            <div className={styles.stats}>
              {STAT_NAMES.map((stat) => (
                <Field key={stat} label={statLabel(stat)} error={errorAt(errors, `stats.${stat}`)}>
                  <input
                    type="number" inputMode="numeric" min="1" max="255" step="1"
                    value={values.stats[stat]}
                    onChange={(e) => set('stats', { ...values.stats, [stat]: e.target.value })}
                  />
                </Field>
              ))}
            </div>
          </fieldset>
        </fieldset>
      )}

      <fieldset className={`card ${styles.section}`}>
        <legend>Proprietary data</legend>
        <div className={styles.grid}>
          <Field label="Region" error={errorAt(errors, 'region')}>
            <input {...bind('region')} placeholder="Kanto" maxLength={50} />
          </Field>
          <Field label="Habitat" error={errorAt(errors, 'habitat')}>
            <input {...bind('habitat')} placeholder="forest" maxLength={50} />
          </Field>
          <Field label="Latitude" error={errorAt(errors, 'coordinates.latitude', 'coordinates')}>
            <input {...bind('latitude')} type="number" inputMode="decimal" step="any" min="-90" max="90" />
          </Field>
          <Field label="Longitude" error={errorAt(errors, 'coordinates.longitude')}>
            <input {...bind('longitude')} type="number" inputMode="decimal" step="any" min="-180" max="180" />
          </Field>
          <Field label="Tags" error={errorAt(errors, 'tags')} hint="Comma-separated lowercase slugs: starter, gen-1" className={styles.wide}>
            <input {...bind('tags')} autoComplete="off" />
          </Field>
        </div>

        <fieldset className={styles.group}>
          <legend>Localized names</legend>
          {errorAt(errors, 'localizedNames') && <p className={styles.groupError}>{errorAt(errors, 'localizedNames')}</p>}
          {values.localizedNames.map((entry, i) => (
            <div key={i} className={styles.localized}>
              <Field label={`Language ${i + 1}`} hint="BCP 47: ja, pt-BR">
                <input
                  value={entry.language}
                  onChange={(e) => set('localizedNames', values.localizedNames.map((n, j) => (j === i ? { ...n, language: e.target.value } : n)))}
                />
              </Field>
              <Field label={`Localized name ${i + 1}`}>
                <input
                  value={entry.name}
                  onChange={(e) => set('localizedNames', values.localizedNames.map((n, j) => (j === i ? { ...n, name: e.target.value } : n)))}
                />
              </Field>
              <button
                type="button"
                className="button"
                aria-label={`Remove localized name ${i + 1}`}
                onClick={() => set('localizedNames', values.localizedNames.filter((_, j) => j !== i))}
              >
                Remove
              </button>
            </div>
          ))}
          <button
            type="button"
            className="button"
            onClick={() => set('localizedNames', [...values.localizedNames, { language: '', name: '' }])}
            disabled={values.localizedNames.length >= 20}
          >
            Add a localized name
          </button>
        </fieldset>
      </fieldset>

      <div className={styles.footer}>
        <button type="submit" className="button primary" disabled={pending}>{pending ? 'Saving…' : submitLabel}</button>
        <Link to={cancelTo} className="button">Cancel</Link>
      </div>
    </form>
  )
}
