import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { accountApi } from '../api/authApi'
import { ApiError } from '../api/client'
import type { Role, UserSummary } from '../api/types'
import { renderPage } from '../test/renderPage'
import ChangePasswordRequiredPage from './ChangePasswordRequiredPage'

vi.mock('../api/authApi', () => ({ accountApi: { changePassword: vi.fn() } }))

const TEMPORARY = 'Temp-Pass#Word4242'
const CHOSEN = 'My-Very-Own#Password2027'

function chosen(role: Role): UserSummary {
  return { id: 1, fullName: 'Session Tester', email: 'ada@example.com', phoneNumber: '+263771111111', role, phoneVerified: true, mustChangePassword: false }
}

function renderScreen(options: { role?: Role; mustChangePassword?: boolean; signedOut?: boolean } = {}) {
  return renderPage(<ChangePasswordRequiredPage />, {
    role: options.role ?? 'APPLICANT_DRIVER',
    route: '/change-password',
    mustChangePassword: options.mustChangePassword ?? true,
    signedOut: options.signedOut,
  })
}

async function fill(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/^temporary password/i), TEMPORARY)
  await user.type(screen.getByLabelText(/^new password/i), CHOSEN)
  await user.type(screen.getByLabelText(/^confirm new password/i), CHOSEN)
}

describe('choose your own password', () => {
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it('explains why the person is here and asks for the temporary password', () => {
    renderScreen()

    expect(screen.getByRole('heading', { level: 1, name: /choose your own password/i })).toBeInTheDocument()
    expect(screen.getByText(/signed in with a temporary password/i)).toBeInTheDocument()
    expect(screen.getByText('Signed in as ada@example.com')).toBeInTheDocument()
    expect(screen.getByLabelText(/^temporary password/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Set my password' })).toBeDisabled()
  })

  it.each([
    ['APPLICANT_DRIVER', '/driver/dashboard'],
    ['LOGISTICS_ADMIN', '/admin/dashboard'],
  ] as const)('after a %s sets a password, drops the flag from the session and opens their own dashboard', async (role, dashboard) => {
    vi.mocked(accountApi.changePassword).mockResolvedValue(chosen(role))
    const user = userEvent.setup()
    renderScreen({ role })

    await fill(user)
    await user.click(screen.getByRole('button', { name: 'Set my password' }))

    await waitFor(() => expect(accountApi.changePassword).toHaveBeenCalledWith({ currentPassword: TEMPORARY, newPassword: CHOSEN }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(dashboard))
    const stored = JSON.parse(window.localStorage.getItem('takeoff.auth') ?? '{}')
    expect(stored.user.mustChangePassword).toBe(false)
    expect(stored.accessToken).toBe('test-token') // the same session carries on: no second sign-in
  })

  it('shows a wrong temporary password under its field and stays put', async () => {
    vi.mocked(accountApi.changePassword).mockRejectedValue(
      new ApiError({
        message: 'Some of the information you entered is not valid.',
        status: 400,
        code: 'VALIDATION_FAILED',
        fieldErrors: { currentPassword: ['Your current password is not correct.'] },
      }),
    )
    const user = userEvent.setup()
    renderScreen()

    await fill(user)
    await user.click(screen.getByRole('button', { name: 'Set my password' }))

    expect(await screen.findByText('Your current password is not correct.')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/change-password')
    expect(JSON.parse(window.localStorage.getItem('takeoff.auth') ?? '{}').user.mustChangePassword).toBe(true)
  })

  it('lets the person sign out instead', async () => {
    const user = userEvent.setup()
    renderScreen()

    await user.click(screen.getByRole('button', { name: /sign out/i }))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
    expect(window.localStorage.getItem('takeoff.auth')).toBeNull()
  })

  it('is never a dead end: sends signed-out visitors to sign in and everyone else to their dashboard', async () => {
    const signedOut = renderScreen({ signedOut: true })
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/))
    signedOut.unmount()

    renderScreen({ role: 'LOGISTICS_ADMIN', mustChangePassword: false })
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/admin/dashboard'))
  })
})
