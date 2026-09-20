import { render } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { Role } from '../api/types'
import { AuthProvider } from '../context/AuthContext'
import { ToastProvider } from '../context/ToastContext'
import { LocationProbe } from './LocationProbe'

const SESSION_KEY = 'takeoff.auth'

// A name no test data uses, so text queries never match the sidebar's "signed in as" entry by accident.
export function seedSession(role: Role, fullName = 'Session Tester') {
  window.localStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      accessToken: 'test-token',
      expiresAt: Date.now() + 600_000,
      user: { id: 1, fullName, email: 'ada@example.com', phoneNumber: '+263771111111', role, phoneVerified: true },
    }),
  )
}

/** Renders a page inside the real providers, signed in as `role`, at `path` matched by the route pattern `route`. */
export function renderPage(ui: ReactElement, options: { role: Role; route: string; path?: string }) {
  seedSession(options.role)
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
