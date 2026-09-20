import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it } from 'vitest'
import type { Role } from '../../api/types'
import { AuthProvider } from '../../context/AuthContext'
import { Sidebar } from './Sidebar'

const SESSION_KEY = 'takeoff.auth'

function seedSession(role: Role, fullName = 'Ada Lovelace') {
  window.localStorage.setItem(
    SESSION_KEY,
    JSON.stringify({
      accessToken: 'test-token',
      expiresAt: Date.now() + 60_000,
      user: { id: 1, fullName, email: 'ada@example.com', phoneNumber: '+15550199', role, phoneVerified: true },
    }),
  )
}

function renderSidebar(path: string) {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/" element={<p>sign in page</p>} />
          <Route
            path="*"
            element={
              <>
                <Sidebar />
                <p>page content</p>
              </>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('Sidebar', () => {
  afterEach(() => window.localStorage.clear())

  it('offers a driver only driver destinations, and no notifications link (the bell lives in the header)', () => {
    seedSession('APPLICANT_DRIVER')
    renderSidebar('/driver/dashboard')

    expect(screen.getByRole('link', { name: 'My application' })).toHaveAttribute('href', '/driver/application')
    expect(screen.queryByRole('link', { name: 'Applications' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /notifications/i })).not.toBeInTheDocument()
  })

  it('offers an administrator only admin destinations', () => {
    seedSession('LOGISTICS_ADMIN', 'TakeOFF Administrator')
    renderSidebar('/admin/dashboard')

    expect(screen.getByRole('link', { name: 'Applications' })).toHaveAttribute('href', '/admin/applications')
    expect(screen.queryByRole('link', { name: 'My application' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /notifications/i })).not.toBeInTheDocument()
  })

  it('shows the brand, the current page, the signed-in user and their role', () => {
    seedSession('APPLICANT_DRIVER')
    renderSidebar('/driver/dashboard')

    const link = screen.getByRole('link', { name: 'Dashboard' })
    expect(link).toHaveAttribute('href', '/driver/dashboard')
    expect(link).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Ada Lovelace')).toBeInTheDocument()
    expect(screen.getByText('Driver applicant')).toBeInTheDocument()
    expect(screen.getByText('AL')).toBeInTheDocument() // initials avatar
  })

  it('points administrators at the admin dashboard and labels them as such', () => {
    seedSession('LOGISTICS_ADMIN', 'TakeOFF Administrator')
    renderSidebar('/admin/dashboard')

    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('href', '/admin/dashboard')
    expect(screen.getByText('Administrator')).toBeInTheDocument()
  })

  it('signs the user out and returns to the sign-in page', async () => {
    seedSession('APPLICANT_DRIVER')
    const user = userEvent.setup()
    renderSidebar('/driver/dashboard')

    await user.click(screen.getByRole('button', { name: /sign out/i }))

    expect(await screen.findByText('sign in page')).toBeInTheDocument()
    expect(window.localStorage.getItem(SESSION_KEY)).toBeNull()
  })

  it('renders nothing for a signed-out visitor', () => {
    renderSidebar('/driver/dashboard')
    expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument()
  })

  describe('mobile drawer', () => {
    it('opens from the menu button, moves focus inside, and closes with Escape', async () => {
      seedSession('APPLICANT_DRIVER')
      const user = userEvent.setup()
      renderSidebar('/driver/dashboard')

      const menuButton = screen.getByRole('button', { name: /open navigation menu/i })
      expect(menuButton).toHaveAttribute('aria-expanded', 'false')
      expect(screen.queryByRole('dialog')).not.toBeInTheDocument()

      await user.click(menuButton)
      const dialog = await screen.findByRole('dialog', { name: /navigation menu/i })
      expect(dialog).toHaveAttribute('aria-modal', 'true')
      expect(menuButton).toHaveAttribute('aria-expanded', 'true')
      expect(screen.getByRole('button', { name: /close navigation menu/i })).toHaveFocus()

      await user.keyboard('{Escape}')
      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
      expect(menuButton).toHaveFocus() // focus returns to where the user was
    })

    it('closes with the close button', async () => {
      seedSession('APPLICANT_DRIVER')
      const user = userEvent.setup()
      renderSidebar('/driver/dashboard')

      await user.click(screen.getByRole('button', { name: /open navigation menu/i }))
      await screen.findByRole('dialog')
      await user.click(screen.getByRole('button', { name: /close navigation menu/i }))

      await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    })

    it('keeps Tab focus inside the open drawer', async () => {
      seedSession('APPLICANT_DRIVER')
      const user = userEvent.setup()
      renderSidebar('/driver/dashboard')

      await user.click(screen.getByRole('button', { name: /open navigation menu/i }))
      const dialog = await screen.findByRole('dialog')

      // close button -> Dashboard -> My application -> Sign out, then Tab wraps back to the close button
      for (let i = 0; i < 4; i++) await user.tab()
      expect(dialog).toContainElement(document.activeElement as HTMLElement)
      expect(screen.getByRole('button', { name: /close navigation menu/i })).toHaveFocus()
    })
  })
})
