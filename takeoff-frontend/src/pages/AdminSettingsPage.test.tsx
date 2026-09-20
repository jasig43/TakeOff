import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reviewApi } from '../api/applicationApi'
import { accountApi, adminApi } from '../api/authApi'
import { ApiError } from '../api/client'
import type { UserSummary } from '../api/types'
import { makeUser } from '../test/fixtures'
import { renderPage } from '../test/renderPage'
import AdminSettingsPage from './AdminSettingsPage'

vi.mock('../api/authApi', () => ({
  accountApi: { changePassword: vi.fn() },
  adminApi: { listUsers: vi.fn(), createUser: vi.fn(), setUserRole: vi.fn(), issueTemporaryPassword: vi.fn() },
}))
vi.mock('../api/applicationApi', () => ({
  reviewApi: { summary: vi.fn() },
  notificationApi: { inbox: vi.fn() },
}))

const CURRENT = 'Current#Password2026'
const NEW = 'Brand-New#Password2027'

const changedUser: UserSummary = {
  id: 1,
  fullName: 'Session Tester',
  email: 'ada@example.com',
  phoneNumber: '+263771111111',
  role: 'LOGISTICS_ADMIN',
  phoneVerified: true,
  mustChangePassword: false,
}

function renderSettings() {
  return renderPage(<AdminSettingsPage />, { role: 'LOGISTICS_ADMIN', route: '/admin/settings' })
}

async function fill(user: ReturnType<typeof userEvent.setup>, current: string, next: string, confirm: string) {
  await user.type(screen.getByLabelText(/^current password/i), current)
  await user.type(screen.getByLabelText(/^new password/i), next)
  await user.type(screen.getByLabelText(/^confirm new password/i), confirm)
}

describe('admin settings page', () => {
  beforeEach(() => {
    vi.mocked(reviewApi.summary).mockResolvedValue({ pendingReview: 0, approved: 0, rejected: 0, totalSubmitted: 0 })
    vi.mocked(adminApi.listUsers).mockResolvedValue({ items: [makeUser()], page: 0, size: 10, totalItems: 1, totalPages: 1 })
  })
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it("shows the signed-in administrator's account details", () => {
    renderSettings()

    expect(screen.getByRole('heading', { level: 1, name: 'Settings' })).toBeInTheDocument()
    // the sidebar shows the name and role too, so look inside the Account card
    const account = within(screen.getByText('Name').closest('dl') as HTMLElement)
    expect(account.getByText('Session Tester')).toBeInTheDocument() // the name from the session
    expect(account.getByText('ada@example.com')).toBeInTheDocument()
    expect(account.getByText('+263771111111')).toBeInTheDocument()
    expect(account.getByText('Administrator')).toBeInTheDocument()
  })

  describe('tabs', () => {
    it('opens on Account and security, and switches to Users and roles', async () => {
      const user = userEvent.setup()
      renderSettings()

      expect(screen.getByRole('tab', { name: 'Account and security' })).toHaveAttribute('aria-selected', 'true')
      expect(screen.getByRole('tabpanel')).toHaveTextContent(/change password/i)
      expect(adminApi.listUsers).not.toHaveBeenCalled() // nothing is fetched until that tab is opened

      await user.click(screen.getByRole('tab', { name: 'Users and roles' }))

      expect(screen.getByRole('tab', { name: 'Users and roles' })).toHaveAttribute('aria-selected', 'true')
      expect(await screen.findByText('Grace Hopper')).toBeInTheDocument()
      expect(screen.queryByLabelText(/^current password/i)).not.toBeInTheDocument()
    })

    it('moves between tabs with the arrow keys', async () => {
      const user = userEvent.setup()
      renderSettings()

      screen.getByRole('tab', { name: 'Account and security' }).focus()
      await user.keyboard('{ArrowRight}')
      expect(screen.getByRole('tab', { name: 'Users and roles' })).toHaveAttribute('aria-selected', 'true')
      expect(screen.getByRole('tab', { name: 'Users and roles' })).toHaveFocus()

      await user.keyboard('{ArrowRight}') // wraps around
      expect(screen.getByRole('tab', { name: 'Account and security' })).toHaveAttribute('aria-selected', 'true')
    })
  })

  describe('change password', () => {
    it('keeps the button disabled until the current password is given, the new one meets the rules and both match', async () => {
      const user = userEvent.setup()
      renderSettings()
      const submit = screen.getByRole('button', { name: 'Change password' })
      expect(submit).toBeDisabled()

      await fill(user, CURRENT, 'weak', 'weak')
      expect(submit).toBeDisabled() // new password breaks the policy

      await user.clear(screen.getByLabelText(/^new password/i))
      await user.clear(screen.getByLabelText(/^confirm new password/i))
      await user.type(screen.getByLabelText(/^new password/i), NEW)
      await user.type(screen.getByLabelText(/^confirm new password/i), 'Something#Different2027')
      expect(await screen.findByText('The two passwords must match.')).toBeInTheDocument()
      expect(submit).toBeDisabled()

      await user.clear(screen.getByLabelText(/^confirm new password/i))
      await user.type(screen.getByLabelText(/^confirm new password/i), NEW)
      expect(submit).toBeEnabled()
    })

    it('changes the password, tells the admin, and clears every password field', async () => {
      vi.mocked(accountApi.changePassword).mockResolvedValue(changedUser)
      const user = userEvent.setup()
      renderSettings()

      await fill(user, CURRENT, NEW, NEW)
      await user.click(screen.getByRole('button', { name: 'Change password' }))

      await waitFor(() => expect(accountApi.changePassword).toHaveBeenCalledWith({ currentPassword: CURRENT, newPassword: NEW }))
      expect(await screen.findByText('Your password has been changed.')).toBeInTheDocument()
      expect(screen.getByLabelText(/^current password/i)).toHaveValue('')
      expect(screen.getByLabelText(/^new password/i)).toHaveValue('')
      expect(screen.getByLabelText(/^confirm new password/i)).toHaveValue('')
    })

    it('shows a wrong current password under that field and keeps what was typed', async () => {
      vi.mocked(accountApi.changePassword).mockRejectedValue(
        new ApiError({
          message: 'Some of the information you entered is not valid.',
          status: 400,
          code: 'VALIDATION_FAILED',
          fieldErrors: { currentPassword: ['Your current password is not correct.'] },
        }),
      )
      const user = userEvent.setup()
      renderSettings()

      await fill(user, CURRENT, NEW, NEW)
      await user.click(screen.getByRole('button', { name: 'Change password' }))

      expect(await screen.findByText('Your current password is not correct.')).toBeInTheDocument()
      expect(screen.getByLabelText(/^current password/i)).toBeInvalid()
      expect(screen.getByLabelText(/^new password/i)).toHaveValue(NEW)
      expect(screen.queryByText('Your password has been changed.')).not.toBeInTheDocument()
    })

    it("shows the server's complaint about the new password under the new-password field", async () => {
      vi.mocked(accountApi.changePassword).mockRejectedValue(
        new ApiError({
          message: 'Some of the information you entered is not valid.',
          status: 400,
          code: 'VALIDATION_FAILED',
          fieldErrors: { newPassword: ['Choose a password that is different from your current one.'] },
        }),
      )
      const user = userEvent.setup()
      renderSettings()

      await fill(user, CURRENT, NEW, NEW)
      await user.click(screen.getByRole('button', { name: 'Change password' }))

      expect(await screen.findByText('Choose a password that is different from your current one.')).toBeInTheDocument()
      expect(screen.getByLabelText(/^new password/i)).toBeInvalid()
    })

    it('reports a failure that is not about a field, such as the network being down', async () => {
      vi.mocked(accountApi.changePassword).mockRejectedValue(
        new ApiError({ message: "We couldn't reach the TakeOFF server.", isNetworkError: true }),
      )
      const user = userEvent.setup()
      renderSettings()

      await fill(user, CURRENT, NEW, NEW)
      await user.click(screen.getByRole('button', { name: 'Change password' }))

      expect(await screen.findByRole('alert')).toHaveTextContent("We couldn't reach the TakeOFF server.")
    })

    it('does not send twice when the button is pressed repeatedly', async () => {
      let finish: (value: UserSummary) => void = () => {}
      vi.mocked(accountApi.changePassword).mockReturnValue(new Promise<UserSummary>((resolve) => (finish = resolve)))
      const user = userEvent.setup()
      renderSettings()

      await fill(user, CURRENT, NEW, NEW)
      const submit = screen.getByRole('button', { name: 'Change password' })
      await user.click(submit)
      await user.click(submit)
      finish(changedUser)

      await waitFor(() => expect(accountApi.changePassword).toHaveBeenCalledTimes(1))
    })
  })
})
