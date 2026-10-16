import type { ReactNode } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useSession } from './session'

/** Routes for signed-in users: a visitor is sent to sign in, and comes back here afterwards. */
export function RequireAuth() {
  const { session } = useSession()
  const location = useLocation()
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <Outlet />
}

/** Write screens. The API enforces this too (403); hiding them just spares users a dead end. */
export function AdminOnly({ children }: { children: ReactNode }) {
  const { isAdmin } = useSession()
  if (isAdmin) return children
  return (
    <div className="card" style={{ maxWidth: 560, margin: '2rem auto', padding: '1.5rem', display: 'grid', gap: '0.5rem' }}>
      <h1>Admins only</h1>
      <p className="muted">Only administrators can change the local store. Sign in as an admin to continue.</p>
    </div>
  )
}
