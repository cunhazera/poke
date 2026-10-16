import { Link, NavLink, Outlet } from 'react-router'
import { useSignOut } from '../api/auth'
import { useSession } from '../auth/session'
import styles from './Layout.module.css'

export function Layout() {
  const { user, isAdmin } = useSession()
  const signOut = useSignOut()

  return (
    <div className={styles.shell}>
      <header className={styles.header}>
        <div className={styles.bar}>
          <Link to="/" className={styles.brand}>
            <img src="/favicon.svg" alt="" width={28} height={28} />
            Poke
          </Link>
          <nav aria-label="Main" className={styles.nav}>
            <NavLink to="/" end>Pokédex</NavLink>
            {user && <NavLink to="/local">Local store</NavLink>}
          </nav>
          <div className={styles.account}>
            {user ? (
              <>
                <span className={styles.user}>
                  <span>{user.username}</span>
                  <span className={styles.role}>{isAdmin ? 'Admin' : 'Trainer'}</span>
                </span>
                <button type="button" className="button" onClick={signOut}>Sign out</button>
              </>
            ) : (
              <>
                <Link to="/login" className="button">Sign in</Link>
                <Link to="/register" className={`button primary ${styles.register}`}>Register</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
      <footer className={styles.footer}>
        Data from <a href="https://pokeapi.co" target="_blank" rel="noreferrer">PokeAPI</a>. Pokémon and its names are trademarks of Nintendo.
      </footer>
    </div>
  )
}
