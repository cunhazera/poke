import { type FormEvent, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { useRegister, useSignIn } from '../api/auth'
import { ApiError } from '../api/client'
import { Field } from '../components/Field'
import styles from './AuthPages.module.css'

/** Where to go after signing in: back to the page that asked for it, else home. */
function useReturnTo() {
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from
  return from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}

export function LoginPage() {
  const navigate = useNavigate()
  const returnTo = useReturnTo()
  const signIn = useSignIn()
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')

  const submit = (event: FormEvent) => {
    event.preventDefault()
    signIn.mutate({ login: login.trim(), password }, { onSuccess: () => navigate(returnTo, { replace: true }) })
  }

  return (
    <form className={`card ${styles.form}`} onSubmit={submit}>
      <h1>Sign in</h1>
      <p className="muted">Demo accounts: <code>ash</code> / <code>pikachu123</code> (trainer), <code>oak</code> / <code>professor123</code> (admin).</p>
      {signIn.error && <p role="alert" className="notice error">{signIn.error.message}</p>}
      <Field label="Username or email">
        <input value={login} onChange={(e) => setLogin(e.target.value)} autoComplete="username" required />
      </Field>
      <Field label="Password">
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
      </Field>
      <button type="submit" className="button primary" disabled={signIn.isPending}>
        {signIn.isPending ? 'Signing in…' : 'Sign in'}
      </button>
      <p className="muted">New here? <Link to="/register" state={{ from: returnTo }}>Create an account</Link></p>
    </form>
  )
}

/** The API's conflicts carry no `field`; their title says which input they concern. */
const CONFLICT_FIELDS: Record<string, string> = { 'Username taken': 'username', 'Email already registered': 'email' }

function registrationErrors(error: Error | null): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  const field = CONFLICT_FIELDS[error.problem.title ?? '']
  return field ? { [field]: error.message } : error.fieldErrors
}

export function RegisterPage() {
  const navigate = useNavigate()
  const returnTo = useReturnTo()
  const register = useRegister()
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const errors = registrationErrors(register.error)
  const unplaced = register.error && Object.keys(errors).length === 0

  const set = (key: keyof typeof form) => (event: { target: { value: string } }) => setForm({ ...form, [key]: event.target.value })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    register.mutate(
      { username: form.username.trim(), email: form.email.trim(), password: form.password },
      { onSuccess: () => navigate(returnTo, { replace: true }) },
    )
  }

  return (
    <form className={`card ${styles.form}`} onSubmit={submit}>
      <h1>Create an account</h1>
      <p className="muted">Trainers can browse the local store; editing it is for admins.</p>
      {unplaced && <p role="alert" className="notice error">{register.error.message}</p>}
      <Field label="Username" error={errors.username} hint="3–50 letters, digits, dots, dashes or underscores.">
        <input value={form.username} onChange={set('username')} autoComplete="username" required minLength={3} maxLength={50} />
      </Field>
      <Field label="Email" error={errors.email}>
        <input type="email" value={form.email} onChange={set('email')} autoComplete="email" required maxLength={254} />
      </Field>
      <Field label="Password" error={errors.password} hint="At least 8 characters.">
        <input type="password" value={form.password} onChange={set('password')} autoComplete="new-password" required minLength={8} />
      </Field>
      <button type="submit" className="button primary" disabled={register.isPending}>
        {register.isPending ? 'Creating account…' : 'Create account'}
      </button>
      <p className="muted">Already registered? <Link to="/login" state={{ from: returnTo }}>Sign in</Link></p>
    </form>
  )
}
