import { screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reviewApi } from '../api/applicationApi'
import { renderPage } from '../test/renderPage'
import AdminDashboardPage from './AdminDashboardPage'

vi.mock('../api/applicationApi', () => ({
  reviewApi: { summary: vi.fn(), list: vi.fn() },
  notificationApi: { inbox: vi.fn() },
}))

function renderDashboard() {
  return renderPage(<AdminDashboardPage />, { role: 'LOGISTICS_ADMIN', route: '/admin/dashboard' })
}

describe('admin dashboard', () => {
  beforeEach(() => {
    vi.mocked(reviewApi.summary).mockResolvedValue({ pendingReview: 3, approved: 5, rejected: 2, totalSubmitted: 10 })
    vi.mocked(reviewApi.list).mockResolvedValue({
      items: [
        {
          id: 9,
          referenceId: 'TKO-9',
          status: 'PENDING_REVIEW',
          driverName: 'Grace Hopper',
          driverEmail: 'grace@example.com',
          driverPhone: '+263772222222',
          plateNumber: 'XYZ 987',
          submittedAt: '2026-09-20T10:00:00Z',
          decidedAt: null,
        },
      ],
      page: 0,
      size: 5,
      totalItems: 1,
      totalPages: 1,
    })
  })
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it('shows the counts as links into the filtered application list', async () => {
    renderDashboard()

    const pending = await screen.findByRole('link', { name: 'Pending review: 3' })
    expect(pending).toHaveAttribute('href', '/admin/applications?status=PENDING_REVIEW')
    expect(screen.getByRole('link', { name: 'Approved: 5' })).toHaveAttribute('href', '/admin/applications?status=APPROVED')
    expect(screen.getByRole('link', { name: 'Not approved: 2' })).toHaveAttribute('href', '/admin/applications?status=REJECTED')
    expect(screen.getByRole('link', { name: 'Total submitted: 10' })).toHaveAttribute('href', '/admin/applications')
  })

  it('lists the applications waiting for review, newest work first', async () => {
    renderDashboard()

    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument()
    expect(reviewApi.list).toHaveBeenCalledWith({ status: 'PENDING_REVIEW', page: 0, size: 5 })
    expect(screen.getByRole('link', { name: /review application tko-9 from grace hopper/i })).toHaveAttribute(
      'href',
      '/admin/applications/9',
    )
  })

  it('has no driver-application content', async () => {
    renderDashboard()
    await screen.findByRole('link', { name: 'Pending review: 3' })

    expect(screen.queryByRole('link', { name: /start your application/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /notifications/i })).not.toBeInTheDocument()
  })
})
