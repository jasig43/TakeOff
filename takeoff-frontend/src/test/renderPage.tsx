import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { Role } from '../api/types'
import { AuthProvider } from '../context/AuthContext'
import { ToastProvider } from '../context/ToastContext'
import { LocationProbe } from './LocationProbe'

const SESSION_KEY = 'takeoff.auth'

// A name no test data uses, so text queries never match the sidebar's "signed in as" entry by accident.
export function seedSession(role: Role, fullName = 'Session Tester', mustChangePassword = false) {
  window.localStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      accessToken: 'test-token',
      expiresAt: Date.now() + 600_000,
      user: { id: 1, fullName, email: 'ada@example.com', phoneNumber: '+263771111111', role, phoneVerified: true, mustChangePassword },
    }),
  )
}

interface RenderPageOptions {
  role: Role
  /** The route pattern the page is mounted on. */
  route: string
  /** The URL to start at, when it differs from the pattern (for example one with a real id in it). */
  path?: string
  /** Sign in as someone who is still on a temporary password. */
  mustChangePassword?: boolean
  /** Start with nobody signed in. */
  signedOut?: boolean
}

/** Renders a page inside the real providers, signed in as `role`, at `path` matched by the route pattern `route`. */
export function renderPage(ui: ReactElement, options: RenderPageOptions) {
  window.localStorage.clear()
  if (!options.signedOut) seedSession(options.role, undefined, options.mustChangePassword)
  return render(
    <ToastProvider>
      <AuthProvider>
        <MemoryRouter initialEntries={[options.path ?? options.route]}>
          <Routes>
            <Route path={options.route} element={ui} />
            <Route path="*" element={<p>other page</p>} />
          </Routes>
          <LocationProbe />
        </MemoryRouter>
      </AuthProvider>
    </ToastProvider>,
  )
}
