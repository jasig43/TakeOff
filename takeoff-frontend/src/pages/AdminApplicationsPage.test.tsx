import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { reviewApi } from '../api/applicationApi'
import type { ApplicationSummaryRow, PageOf } from '../api/types'
import { renderPage } from '../test/renderPage'
import AdminApplicationsPage from './AdminApplicationsPage'

vi.mock('../api/applicationApi', () => ({
  reviewApi: { summary: vi.fn(), list: vi.fn(), get: vi.fn(), documentBlob: vi.fn(), decide: vi.fn() },
  notificationApi: { inbox: vi.fn() },
}))

const api = vi.mocked(reviewApi)

function row(overrides: Partial<ApplicationSummaryRow>): ApplicationSummaryRow {
  return {
    id: 1,
    referenceId: 'TKO-1',
    status: 'PENDING_REVIEW',
    driverName: 'Ada Lovelace',
    driverEmail: 'ada@example.com',
    driverPhone: '+263771111111',
    plateNumber: 'ABC 1234',
    submittedAt: '2026-09-20T10:00:00Z',
    decidedAt: null,
    ...overrides,
  }
}

function pageOf(items: ApplicationSummaryRow[], extra: Partial<PageOf<ApplicationSummaryRow>> = {}): PageOf<ApplicationSummaryRow> {
  return { items, page: 0, size: 10, totalItems: items.length, totalPages: items.length ? 1 : 0, ...extra }
}

function renderList(path = '/admin/applications') {
  return renderPage(<AdminApplicationsPage />, { role: 'LOGISTICS_ADMIN', route: '/admin/applications', path })
}

describe('admin applications page', () => {
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it('lists submitted applications with a link to review each one', async () => {
    api.list.mockResolvedValue(
      pageOf([row({}), row({ id: 2, referenceId: 'TKO-2', driverName: 'Grace Hopper', status: 'APPROVED' })]),
    )
    renderList()

    expect(await screen.findByText('Grace Hopper')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /review application tko-1 from ada lovelace/i })).toHaveAttribute(
      'href',
      '/admin/applications/1',
    )
    // the status is in the results table as well as in the filter's options
    expect(within(screen.getByRole('table')).getByText('Approved')).toBeInTheDocument()
    expect(api.list).toHaveBeenCalledWith({ status: undefined, q: '', page: 0, size: 10 })
  })

  it('filters by status and keeps the filter in the URL', async () => {
    api.list.mockResolvedValue(pageOf([row({})]))
    const user = userEvent.setup()
    renderList()
    await screen.findByText('Ada Lovelace')

    await user.selectOptions(screen.getByLabelText('Status'), 'APPROVED')

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ status: 'APPROVED', q: '', page: 0, size: 10 }))
    expect(screen.getByTestId('location')).toHaveTextContent('/admin/applications?status=APPROVED')
  })

  it('starts from the status in the URL (dashboard tiles link here)', async () => {
    api.list.mockResolvedValue(pageOf([row({ status: 'REJECTED' })]))
    renderList('/admin/applications?status=REJECTED')

    await screen.findByText('Ada Lovelace')
    expect(api.list).toHaveBeenCalledWith({ status: 'REJECTED', q: '', page: 0, size: 10 })
    expect(screen.getByLabelText('Status')).toHaveValue('REJECTED')
  })

  it('searches after the user stops typing, not on every keystroke', async () => {
    api.list.mockResolvedValue(pageOf([row({})]))
    const user = userEvent.setup()
    renderList()
    await screen.findByText('Ada Lovelace')
    api.list.mockClear()

    await user.type(screen.getByLabelText('Search'), 'grace')

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ status: undefined, q: 'grace', page: 0, size: 10 }))
    // no request per keystroke: only the settled term was searched
    expect(api.list.mock.calls.every(([query]) => query.q === '' || query.q === 'grace')).toBe(true)
  })

  it('pages through results', async () => {
    api.list.mockResolvedValue(pageOf([row({})], { totalItems: 12, totalPages: 2 }))
    const user = userEvent.setup()
    renderList()
    await screen.findByText('Ada Lovelace')

    expect(screen.getByRole('button', { name: /previous/i })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: /next/i }))

    await waitFor(() => expect(api.list).toHaveBeenLastCalledWith({ status: undefined, q: '', page: 1, size: 10 }))
    expect(screen.getByTestId('location')).toHaveTextContent('page=1')
  })

  it('explains an empty result', async () => {
    api.list.mockResolvedValue(pageOf([]))
    renderList('/admin/applications?status=APPROVED')

    expect(await screen.findByText('No applications found')).toBeInTheDocument()
    expect(screen.getByText(/try a different search or filter/i)).toBeInTheDocument()
  })

  it('reports a failed load and can retry', async () => {
    api.list.mockRejectedValueOnce(new Error('boom'))
    api.list.mockResolvedValueOnce(pageOf([row({})]))
    const user = userEvent.setup()
    renderList()

    expect(await screen.findByRole('alert')).toHaveTextContent('We could not load the applications.')
    await user.click(screen.getByRole('button', { name: /try again/i }))
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument()
  })
})
