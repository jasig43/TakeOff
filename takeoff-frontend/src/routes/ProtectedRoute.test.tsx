import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import { AuthProvider } from '../context/AuthContext'
import { ProtectedRoute } from './ProtectedRoute'

const SESSION_KEY = 'takeoff.auth'

function seedSession(role: 'APPLICANT_DRIVER' | 'LOGISTICS_ADMIN' | null) {
  window.localStorage.clear()
  if (!role) return
  window.localStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      accessToken: 'test-token',
      expiresAt: Date.now() + 60_000,
      user: { id: 1, fullName: 'Test User', email: 't@example.com', phoneNumber: '+15550199', role, phoneVerified: true },
    }),
  )
}

function renderAt(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={<p>sign in page</p>} />
          <Route element={<ProtectedRoute role="APPLICANT_DRIVER" />}>
            <Route path="/driver/dashboard" element={<p>driver dashboard</p>} />
          </Route>
          <Route element={<ProtectedRoute role="LOGISTICS_ADMIN" />}>
            <Route path="/admin/dashboard" element={<p>admin dashboard</p>} />
          </Route>
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('ProtectedRoute', () => {
  afterEach(() => window.localStorage.clear())

  it('sends signed-out visitors of driver routes to the sign-in page', () => {
    seedSession(null)
    renderAt('/driver/dashboard')
    expect(screen.getByText('sign in page')).toBeInTheDocument()
  })

  it('sends signed-out visitors of admin routes to the sign-in page', () => {
    seedSession(null)
    renderAt('/admin/dashboard')
    expect(screen.getByText('sign in page')).toBeInTheDocument()
  })

  it('lets an applicant into the driver dashboard', () => {
    seedSession('APPLICANT_DRIVER')
    renderAt('/driver/dashboard')
    expect(screen.getByText('driver dashboard')).toBeInTheDocument()
  })

  it('keeps applicants out of admin routes and sends them to their own dashboard', () => {
    seedSession('APPLICANT_DRIVER')
    renderAt('/admin/dashboard')
    expect(screen.queryByText('admin dashboard')).not.toBeInTheDocument()
    expect(screen.getByText('driver dashboard')).toBeInTheDocument()
  })

  it('keeps administrators out of applicant-only routes and sends them to the admin dashboard', () => {
    seedSession('LOGISTICS_ADMIN')
    renderAt('/driver/dashboard')
    expect(screen.queryByText('driver dashboard')).not.toBeInTheDocument()
    expect(screen.getByText('admin dashboard')).toBeInTheDocument()
  })

  it('treats an expired stored session as signed out', () => {
    window.localStorage.setItem(
      SESSION_KEY,
      JSON.stringify({
        accessToken: 'expired',
        expiresAt: Date.now() - 1000,
        user: { id: 1, fullName: 'T', email: 't@example.com', phoneNumber: '+15550199', role: 'APPLICANT_DRIVER', phoneVerified: true },
      }),
    )
    renderAt('/driver/dashboard')
    expect(screen.getByText('sign in page')).toBeInTheDocument()
    expect(window.localStorage.getItem(SESSION_KEY)).toBeNull()
  })
})
