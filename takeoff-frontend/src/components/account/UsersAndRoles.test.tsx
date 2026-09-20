import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { adminApi } from '../../api/authApi'
import { ApiError } from '../../api/client'
import type { AdminUser, IssuedCredential, PageOf } from '../../api/types'
import { makeUser } from '../../test/fixtures'
import { renderPage } from '../../test/renderPage'
import { UsersAndRoles } from './UsersAndRoles'

vi.mock('../../api/authApi', () => ({
  adminApi: { listUsers: vi.fn(), createUser: vi.fn(), setUserRole: vi.fn(), issueTemporaryPassword: vi.fn() },
}))
vi.mock('../../api/applicationApi', () => ({
  reviewApi: { summary: vi.fn() },
  notificationApi: { inbox: vi.fn() },
}))

const api = vi.mocked(adminApi)

const ME = makeUser({ id: 1, fullName: 'Session Tester', email: 'ada@example.com', role: 'LOGISTICS_ADMIN' }) // the signed-in admin (id 1)
const GRACE = makeUser({ id: 2 })
const ALAN = makeUser({
  id: 3,
  fullName: 'Alan Turing',
  email: 'alan@example.com',
  mustChangePassword: true,
  temporaryPasswordExpiresAt: '2099-01-01T00:00:00Z',
})

function pageOf(items: AdminUser[], extra: Partial<PageOf<AdminUser>> = {}): PageOf<AdminUser> {
  return { items, page: 0, size: 10, totalItems: items.length, totalPages: items.length ? 1 : 0, ...extra }
}

function credential(user: AdminUser, temporaryPassword = 'Temp-Pass#Word4242'): IssuedCredential {
  return { user, temporaryPassword, temporaryPasswordExpiresAt: '2099-01-04T10:00:00Z' }
}

async function openForm(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: 'Create account' }))
  return within(screen.getByRole('form', { name: 'New account' }))
}

function renderPanel() {
  return renderPage(<UsersAndRoles />, { role: 'LOGISTICS_ADMIN', route: '/' })
}

describe('users and roles', () => {
  beforeEach(async () => {
    const { reviewApi } = await import('../../api/applicationApi')
    vi.mocked(reviewApi.summary).mockResolvedValue({ pendingReview: 0, approved: 0, rejected: 0, totalSubmitted: 0 })
    api.listUsers.mockResolvedValue(pageOf([ME, GRACE, ALAN]))
  })
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  describe('the list', () => {
    it('shows every account with its role and status', async () => {
      renderPanel()

      expect(await screen.findByText('Grace Hopper')).toBeInTheDocument()
      expect(screen.getByText('alan@example.com')).toBeInTheDocument()
      expect(screen.getByLabelText('Role for Grace Hopper')).toHaveValue('APPLICANT_DRIVER')
      expect(screen.getByLabelText('Role for Session Tester')).toHaveValue('LOGISTICS_ADMIN')
      expect(screen.getByText(/temporary password until/i)).toBeInTheDocument() // Alan has not chosen his own yet
      expect(screen.getAllByText('Active').length).toBe(2)
      expect(api.listUsers).toHaveBeenCalledWith({ q: '', page: 0, size: 10 })
    })

    it('marks the signed-in administrator, and gives them no way to change their own role or password', async () => {
      renderPanel()
      await screen.findByText('Grace Hopper')

      expect(screen.getByText('You')).toBeInTheDocument()
      expect(screen.getByLabelText('Role for Session Tester')).toBeDisabled()
      expect(screen.queryByRole('button', { name: /new temporary password for session tester/i })).not.toBeInTheDocument()
      expect(screen.getByRole('button', { name: /new temporary password for grace hopper/i })).toBeInTheDocument()
    })

    it('flags a temporary password that has already expired', async () => {
      api.listUsers.mockResolvedValue(pageOf([makeUser({ mustChangePassword: true, temporaryPasswordExpiresAt: '2020-01-01T00:00:00Z' })]))
      renderPanel()

      expect(await screen.findByText('Temporary password expired')).toBeInTheDocument()
    })

    it('searches after the user stops typing and starts again from the first page', async () => {
      const user = userEvent.setup()
      renderPanel()
      await screen.findByText('Grace Hopper')
      api.listUsers.mockClear()

      await user.type(screen.getByLabelText('Search accounts'), 'alan')

      await waitFor(() => expect(api.listUsers).toHaveBeenLastCalledWith({ q: 'alan', page: 0, size: 10 }))
      expect(api.listUsers.mock.calls.every(([query]) => query.q === '' || query.q === 'alan')).toBe(true)
    })

    it('pages through accounts', async () => {
      api.listUsers.mockResolvedValue(pageOf([GRACE], { totalItems: 12, totalPages: 2 }))
      const user = userEvent.setup()
      renderPanel()
      await screen.findByText('Grace Hopper')

      expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
      await user.click(screen.getByRole('button', { name: /next/i }))

      await waitFor(() => expect(api.listUsers).toHaveBeenLastCalledWith({ q: '', page: 1, size: 10 }))
    })

    it('explains an empty search and reports a failed load with a retry', async () => {
      api.listUsers.mockResolvedValueOnce(pageOf([]))
      const first = renderPanel()
      expect(await screen.findByText('No accounts found')).toBeInTheDocument()
      first.unmount()

      api.listUsers.mockRejectedValueOnce(new Error('boom'))
      api.listUsers.mockResolvedValueOnce(pageOf([GRACE]))
      const user = userEvent.setup()
      renderPanel()

      expect(await screen.findByRole('alert')).toHaveTextContent('We could not load the accounts.')
      await user.click(screen.getByRole('button', { name: /try again/i }))
      expect(await screen.findByText('Grace Hopper')).toBeInTheDocument()
    })
  })

  describe('creating an account', () => {
    it('validates before calling the server', async () => {
      const user = userEvent.setup()
      renderPanel()
      const form = await openForm(user)

      await user.click(form.getByRole('button', { name: 'Create account' }))

      expect(await screen.findByText(/enter their full name/i)).toBeInTheDocument()
      expect(screen.getByText(/enter a valid email address/i)).toBeInTheDocument()
      expect(screen.getByText(/international format/i, { selector: 'p span' })).toBeInTheDocument()
      expect(api.createUser).not.toHaveBeenCalled()
    })
    it('creates the account and shows the temporary password once, then forgets it', async () => {
      api.createUser.mockResolvedValue(credential(makeUser({ id: 9, fullName: 'Tendai Moyo', email: 'tendai@example.com' })))
      const user = userEvent.setup()
      renderPanel()
      const form = await openForm(user)
      api.listUsers.mockClear()

      await user.type(form.getByLabelText(/^full name/i), '  Tendai Moyo ')
      await user.type(form.getByLabelText(/^email address/i), 'Tendai@Example.com')
      await user.type(form.getByLabelText(/^phone number/i), '+263 77 123 4567')
      await user.selectOptions(form.getByLabelText(/^role/i), 'LOGISTICS_ADMIN')
      await user.click(form.getByRole('button', { name: 'Create account' }))

      await waitFor(() =>
        expect(api.createUser).toHaveBeenCalledWith({
          fullName: 'Tendai Moyo',
          email: 'tendai@example.com',
          phoneNumber: '+263771234567',
          role: 'LOGISTICS_ADMIN',
        }),
      )
      const notice = await screen.findByRole('region', { name: 'Temporary password' })
      expect(within(notice).getByRole('heading', { name: 'Account created' })).toBeInTheDocument()
      expect(within(notice).getByTestId('temporary-password')).toHaveTextContent('Temp-Pass#Word4242')
      expect(within(notice).getByText('tendai@example.com')).toBeInTheDocument()
      expect(within(notice).getByText(/only time it is shown/i)).toBeInTheDocument()
      await waitFor(() => expect(api.listUsers).toHaveBeenCalled()) // the list is refreshed
      expect(screen.queryByLabelText(/^full name/i)).not.toBeInTheDocument() // the form closes

      await user.click(within(notice).getByRole('button', { name: 'Done' }))
      expect(screen.queryByText('Temp-Pass#Word4242')).not.toBeInTheDocument()
    })

    it('copies the temporary password', async () => {
      api.createUser.mockResolvedValue(credential(makeUser({ id: 9 })))
      const user = userEvent.setup()
      renderPanel()
      const form = await openForm(user)
      await user.type(form.getByLabelText(/^full name/i), 'Some One')
      await user.type(form.getByLabelText(/^email address/i), 'some@example.com')
      await user.type(form.getByLabelText(/^phone number/i), '+263771234567')
      await user.click(form.getByRole('button', { name: 'Create account' }))

      await user.click(await screen.findByRole('button', { name: /copy temporary password/i }))

      expect(await navigator.clipboard.readText()).toBe('Temp-Pass#Word4242')
      expect(await screen.findByText('Temporary password copied.')).toBeInTheDocument()
    })

    it('shows a duplicate email and a duplicate phone under their own fields', async () => {
      api.createUser.mockRejectedValueOnce(
        new ApiError({ message: 'An account with this email address already exists.', status: 409, code: 'EMAIL_ALREADY_REGISTERED' }),
      )
      api.createUser.mockRejectedValueOnce(
        new ApiError({ message: 'An account with this phone number already exists.', status: 409, code: 'PHONE_ALREADY_REGISTERED' }),
      )
      const user = userEvent.setup()
      renderPanel()
      const form = await openForm(user)
      await user.type(form.getByLabelText(/^full name/i), 'Some One')
      await user.type(form.getByLabelText(/^email address/i), 'some@example.com')
      await user.type(form.getByLabelText(/^phone number/i), '+263771234567')

      await user.click(form.getByRole('button', { name: 'Create account' }))
      expect(await screen.findByText('An account with this email address already exists.')).toBeInTheDocument()
      expect(form.getByLabelText(/^email address/i)).toBeInvalid()

      await user.click(form.getByRole('button', { name: 'Create account' }))
      expect(await screen.findByText('An account with this phone number already exists.')).toBeInTheDocument()
      expect(form.getByLabelText(/^phone number/i)).toBeInvalid()
    })

    it('refuses a Zimbabwean number with the local leading 0 before asking the server', async () => {
      const user = userEvent.setup()
      renderPanel()
      const form = await openForm(user)
      await user.type(form.getByLabelText(/^full name/i), 'Some One')
      await user.type(form.getByLabelText(/^email address/i), 'some@example.com')
      await user.type(form.getByLabelText(/^phone number/i), '+2630778657160')

      await user.click(form.getByRole('button', { name: 'Create account' }))

      expect(await screen.findByText(/without the leading 0 after \+263/i)).toBeInTheDocument()
      expect(api.createUser).not.toHaveBeenCalled()
    })

    it('can be cancelled', async () => {
      const user = userEvent.setup()
      renderPanel()
      await openForm(user)

      await user.click(screen.getByRole('button', { name: 'Cancel' }))

      expect(screen.queryByLabelText(/^full name/i)).not.toBeInTheDocument()
    })
  })

  describe('assigning roles', () => {
    it('asks for confirmation, then changes the role and refreshes the list', async () => {
      api.setUserRole.mockResolvedValue({ ...GRACE, role: 'LOGISTICS_ADMIN' })
      const user = userEvent.setup()
      renderPanel()
      await screen.findByText('Grace Hopper')
      api.listUsers.mockClear()

      await user.selectOptions(screen.getByLabelText('Role for Grace Hopper'), 'LOGISTICS_ADMIN')
      expect(api.setUserRole).not.toHaveBeenCalled() // choosing is not saving
      await user.click(screen.getByRole('button', { name: 'Save role for Grace Hopper' }))
      const confirm = screen.getByRole('group', { name: 'Confirm for Grace Hopper' })
      expect(within(confirm).getByText(/make grace hopper an administrator/i)).toBeInTheDocument()
      expect(api.setUserRole).not.toHaveBeenCalled() // nothing is sent until confirmed

      await user.click(within(confirm).getByRole('button', { name: 'Yes, change role' }))

      await waitFor(() => expect(api.setUserRole).toHaveBeenCalledWith(2, 'LOGISTICS_ADMIN'))
      expect(await screen.findByText('Grace Hopper is now an administrator.')).toBeInTheDocument()
      await waitFor(() => expect(api.listUsers).toHaveBeenCalled())
    })

    it('can be backed out of at every step', async () => {
      const user = userEvent.setup()
      renderPanel()
      await screen.findByText('Grace Hopper')

      await user.selectOptions(screen.getByLabelText('Role for Grace Hopper'), 'LOGISTICS_ADMIN')
      await user.click(screen.getByRole('button', { name: 'Cancel role change for Grace Hopper' }))
      expect(screen.getByLabelText('Role for Grace Hopper')).toHaveValue('APPLICANT_DRIVER')

      await user.selectOptions(screen.getByLabelText('Role for Grace Hopper'), 'LOGISTICS_ADMIN')
      await user.click(screen.getByRole('button', { name: 'Save role for Grace Hopper' }))
      await user.click(within(screen.getByRole('group', { name: 'Confirm for Grace Hopper' })).getByRole('button', { name: 'Cancel' }))
      expect(screen.queryByRole('group', { name: 'Confirm for Grace Hopper' })).not.toBeInTheDocument()

      // choosing the original role again clears the pending change
      await user.selectOptions(screen.getByLabelText('Role for Grace Hopper'), 'APPLICANT_DRIVER')
      expect(screen.queryByRole('button', { name: 'Save role for Grace Hopper' })).not.toBeInTheDocument()
      expect(api.setUserRole).not.toHaveBeenCalled()
    })

    it('says so when the server refuses, and leaves the choice in place', async () => {
      api.setUserRole.mockRejectedValue(
        new ApiError({ message: 'You cannot change your own role. Ask another administrator to do it.', status: 409, code: 'CANNOT_CHANGE_OWN_ROLE' }),
      )
      const user = userEvent.setup()
      renderPanel()
      await screen.findByText('Grace Hopper')

      await user.selectOptions(screen.getByLabelText('Role for Grace Hopper'), 'LOGISTICS_ADMIN')
      await user.click(screen.getByRole('button', { name: 'Save role for Grace Hopper' }))
      await user.click(screen.getByRole('button', { name: 'Yes, change role' }))

      expect(await screen.findByText(/cannot change your own role/i)).toBeInTheDocument()
      expect(screen.getByLabelText('Role for Grace Hopper')).toHaveValue('LOGISTICS_ADMIN')
    })
  })

  describe('issuing a new temporary password', () => {
    it('asks first, then shows the new password once', async () => {
      api.issueTemporaryPassword.mockResolvedValue(credential(GRACE, 'Fresh-Temp#Word9999'))
      const user = userEvent.setup()
      renderPanel()
      await screen.findByText('Grace Hopper')

      await user.click(screen.getByRole('button', { name: /new temporary password for grace hopper/i }))
      const confirm = screen.getByRole('group', { name: 'Confirm for Grace Hopper' })
      expect(within(confirm).getByText(/current password stops working immediately/i)).toBeInTheDocument()
      expect(api.issueTemporaryPassword).not.toHaveBeenCalled()

      await user.click(within(confirm).getByRole('button', { name: 'Yes, issue it' }))

      const notice = await screen.findByRole('region', { name: 'Temporary password' })
      expect(within(notice).getByRole('heading', { name: 'New temporary password issued' })).toBeInTheDocument()
      expect(within(notice).getByTestId('temporary-password')).toHaveTextContent('Fresh-Temp#Word9999')
      expect(api.issueTemporaryPassword).toHaveBeenCalledWith(2)

      await user.click(within(notice).getByRole('button', { name: 'Done' }))
      expect(screen.queryByText('Fresh-Temp#Word9999')).not.toBeInTheDocument()
    })

    it('tells the admin when it fails and shows no password', async () => {
      api.issueTemporaryPassword.mockRejectedValue(new Error('offline'))
      const user = userEvent.setup()
      renderPanel()
      await screen.findByText('Grace Hopper')

      await user.click(screen.getByRole('button', { name: /new temporary password for grace hopper/i }))
      await user.click(screen.getByRole('button', { name: 'Yes, issue it' }))

      expect(await screen.findByText(/could not issue a new temporary password/i)).toBeInTheDocument()
      expect(screen.queryByTestId('temporary-password')).not.toBeInTheDocument()
    })
  })
})
