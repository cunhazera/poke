import { Route, Routes } from 'react-router'
import { AdminOnly, RequireAuth } from './auth/guards'
import { Layout } from './components/Layout'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { CatalogPage } from './pages/CatalogPage'
import { LocalDetailPage } from './pages/LocalDetailPage'
import { LocalFormPage } from './pages/LocalFormPage'
import { LocalListPage } from './pages/LocalListPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { PokemonDetailPage } from './pages/PokemonDetailPage'

/** Public: the catalog (US01, US02) and the auth forms. Signed in: the local store (US03). Admin: its writes (US04). */
export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<CatalogPage />} />
        <Route path="pokemon/:id" element={<PokemonDetailPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route element={<RequireAuth />}>
          <Route path="local" element={<LocalListPage />} />
          <Route path="local/new" element={<AdminOnly><LocalFormPage /></AdminOnly>} />
          <Route path="local/:id" element={<LocalDetailPage />} />
          <Route path="local/:id/edit" element={<AdminOnly><LocalFormPage /></AdminOnly>} />
        </Route>
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
