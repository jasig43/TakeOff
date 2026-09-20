import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from '../api/authApi'
import { ApiError } from '../api/client'
import type { JwtResponse, Role } from '../api/types'
import { AuthProvider } from '../context/AuthContext'
import { ThemeProvider } from '../context/ThemeContext'
import { ToastProvider } from '../context/ToastContext'
import LoginPage from './LoginPage'

vi.mock('../api/authApi', () => ({ authApi: { login: vi.fn() } }))

function jwtFor(role: Role): JwtResponse {
  return {
    accessToken: 'jwt',
    tokenType: 'Bearer',
    expiresInSeconds: 900,
    user: { id: 1, fullName: 'Test User', email: 'user@example.com', phoneNumber: '+15550199', role, phoneVerified: true },
  }
}

function renderLanding() {
  return render(
    <ThemeProvider>
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/']}>
            <Routes>
              <Route path="/" element={<LoginPage />} />
              <Route path="/register" element={<p>register page</p>} />
              <Route path="/verify-otp" element={<p>verify otp page</p>} />
              <Route path="/driver/dashboard" element={<p>driver dashboard</p>} />
              <Route path="/admin/dashboard" element={<p>admin dashboard</p>} />
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>
    </ThemeProvider>,
  )
}

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/email address/i), 'user@example.com')
  await user.type(screen.getByLabelText(/^password/i), 'Whatever#Password1')
  await user.click(screen.getByRole('button', { name: /^sign in$/i }))
}

describe('landing page (sign in)', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.mocked(authApi.login).mockReset()
  })
  afterEach(() => window.localStorage.clear())

  it('shows only the sign-in form with a link to sign up', () => {
    renderLanding()
    expect(screen.getByRole('heading', { level: 1, name: /sign in/i })).toBeInTheDocument()
    expect(screen.getByLabelText(/email address/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /sign up/i })).toHaveAttribute('href', '/register')
    // none of the old marketing content
    expect(screen.queryByText(/become a driver/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/admin portal/i)).not.toBeInTheDocument()
  })

  it('keeps the submit button disabled until both fields are valid', async () => {
    const user = userEvent.setup()
    renderLanding()
    const submit = screen.getByRole('button', { name: /^sign in$/i })
    expect(submit).toBeDisabled()

    await user.type(screen.getByLabelText(/email address/i), 'not-an-email')
    await user.type(screen.getByLabelText(/^password/i), 'x')
    expect(submit).toBeDisabled()

    await user.clear(screen.getByLabelText(/email address/i))
    await user.type(screen.getByLabelText(/email address/i), 'user@example.com')
    expect(submit).toBeEnabled()
  })

  it('sends a driver to the driver dashboard after login', async () => {
    vi.mocked(authApi.login).mockResolvedValue(jwtFor('APPLICANT_DRIVER'))
    const user = userEvent.setup()
    renderLanding()
    await fillAndSubmit(user)
    expect(await screen.findByText('driver dashboard')).toBeInTheDocument()
    expect(authApi.login).toHaveBeenCalledWith({ email: 'user@example.com', password: 'Whatever#Password1' })
  })

  it('sends an administrator to the admin dashboard from the same form', async () => {
    vi.mocked(authApi.login).mockResolvedValue(jwtFor('LOGISTICS_ADMIN'))
    const user = userEvent.setup()
    renderLanding()
    await fillAndSubmit(user)
    expect(await screen.findByText('admin dashboard')).toBeInTheDocument()
  })

  it('sends an unverified applicant to OTP verification', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError({ message: 'Verify your phone number to finish signing in.', status: 403, code: 'PHONE_NOT_VERIFIED' }),
    )
    const user = userEvent.setup()
    renderLanding()
    await fillAndSubmit(user)
    expect(await screen.findByText('verify otp page')).toBeInTheDocument()
  })

  it('shows the server message and stays on the page for wrong credentials', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError({ message: 'Invalid email or password.', status: 401, code: 'INVALID_CREDENTIALS' }),
    )
    const user = userEvent.setup()
    renderLanding()
    await fillAndSubmit(user)
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.queryByText('driver dashboard')).not.toBeInTheDocument()
    expect(window.localStorage.getItem('takeoff.auth')).toBeNull()
  })
})
