import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { applicationApi, notificationApi } from '../api/applicationApi'
import { driverApi } from '../api/authApi'
import type { DriverProfile } from '../api/types'
import { makeApplication, makeCompleteApplication } from '../test/fixtures'
import { renderPage } from '../test/renderPage'
import DriverDashboardPage from './DriverDashboardPage'

vi.mock('../api/authApi', () => ({ driverApi: { getProfile: vi.fn() } }))
vi.mock('../api/applicationApi', () => ({
  applicationApi: { get: vi.fn() },
  notificationApi: { inbox: vi.fn() },
}))

const profile: DriverProfile = {
  id: 1,
  fullName: 'Ada Lovelace',
  email: 'ada@example.com',
  phoneNumber: '+263771111111',
  phoneVerified: true,
  role: 'APPLICANT_DRIVER',
  createdAt: '2026-08-20T08:00:00Z',
}

function renderDashboard() {
  return renderPage(<DriverDashboardPage />, { role: 'APPLICANT_DRIVER', route: '/driver/dashboard' })
}

describe('driver dashboard', () => {
  beforeEach(() => {
    vi.mocked(driverApi.getProfile).mockResolvedValue(profile)
    vi.mocked(notificationApi.inbox).mockResolvedValue({ items: [], unreadCount: 0 })
  })
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it('invites a new driver to start their application', async () => {
    vi.mocked(applicationApi.get).mockResolvedValue(makeApplication())
    renderDashboard()

    expect(await screen.findByRole('heading', { name: 'Welcome, Ada' })).toBeInTheDocument()
    expect(screen.getByText('Draft')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /start your application/i })).toHaveAttribute('href', '/driver/application')
    expect(screen.getByRole('progressbar', { name: /onboarding progress/i })).toHaveAttribute('aria-valuenow', '29') // 2 of 7
  })

  it('shows the Reference ID and status once submitted, and the latest notifications', async () => {
    vi.mocked(applicationApi.get).mockResolvedValue(
      makeCompleteApplication({ status: 'PENDING_REVIEW', editable: false, referenceId: 'TKO-20260920-AB12CD' }),
    )
    vi.mocked(notificationApi.inbox).mockResolvedValue({
      unreadCount: 1,
      items: [
        { id: 1, type: 'APPLICATION_SUBMITTED', title: 'Application submitted', message: 'We received it.', read: false, createdAt: '2026-09-20T10:00:00Z' },
      ],
    })
    renderDashboard()

    expect(await screen.findByText('TKO-20260920-AB12CD')).toBeInTheDocument()
    expect(screen.getByText('Pending review')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /view submission/i })).toBeInTheDocument()
    expect(await screen.findByText('We received it.')).toBeInTheDocument()
    expect(screen.getByRole('progressbar', { name: /onboarding progress/i })).toHaveAttribute('aria-valuenow', '100')
  })

  it('has no administrator content', async () => {
    vi.mocked(applicationApi.get).mockResolvedValue(makeApplication())
    renderDashboard()
    await screen.findByRole('heading', { name: 'Welcome, Ada' })

    expect(screen.queryByText(/pending review queue|waiting for review/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /^applications$/i })).not.toBeInTheDocument()
  })
})
