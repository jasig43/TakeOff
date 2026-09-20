import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reviewApi } from '../api/applicationApi'
import { ApiError } from '../api/client'
import { makeCompleteApplication, makeDetail } from '../test/fixtures'
import { renderPage } from '../test/renderPage'
import AdminApplicationDetailPage from './AdminApplicationDetailPage'

vi.mock('../api/applicationApi', () => ({
  reviewApi: { summary: vi.fn(), list: vi.fn(), get: vi.fn(), documentBlob: vi.fn(), decide: vi.fn() },
  notificationApi: { inbox: vi.fn() },
}))

const api = vi.mocked(reviewApi)

const pending = () =>
  makeDetail(
    makeCompleteApplication({ status: 'PENDING_REVIEW', editable: false, referenceId: 'TKO-1', submittedAt: '2026-09-20T10:00:00Z' }),
  )

function renderDetail(path = '/admin/applications/7') {
  return renderPage(<AdminApplicationDetailPage />, { role: 'LOGISTICS_ADMIN', route: '/admin/applications/:id', path })
}

describe('admin application detail page', () => {
  beforeEach(() => {
    // the header's review bell asks for the counts on every admin page
    api.summary.mockResolvedValue({ pendingReview: 2, approved: 0, rejected: 0, totalSubmitted: 2 })
  })
  afterEach(() => {
    window.localStorage.clear()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.resetAllMocks()
  })

  it('shows the driver, every section of the application and the decision controls', async () => {
    api.get.mockResolvedValue(pending())
    renderDetail()

    expect(await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })).toBeInTheDocument()
    expect(api.get).toHaveBeenCalledWith(7)
    expect(screen.getByText('ada@example.com')).toBeInTheDocument()
    expect(screen.getByText('63-123456 A 63')).toBeInTheDocument()
    expect(screen.getByText('ABC 1234')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /view driver's licence/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /view vehicle registration/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /view insurance certificate/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Approve' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Reject' })).toBeInTheDocument()
  })

  it('opens a document through the admin endpoint', async () => {
    api.get.mockResolvedValue(pending())
    api.documentBlob.mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }))
    const tab = { location: { href: '' }, close: vi.fn() }
    vi.spyOn(window, 'open').mockReturnValue(tab as unknown as Window)
    vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:doc'), revokeObjectURL: vi.fn() })
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: /view insurance certificate/i }))

    await waitFor(() => expect(api.documentBlob).toHaveBeenCalledWith(7, 'INSURANCE'))
    await waitFor(() => expect(tab.location.href).toBe('blob:doc'))
  })

  it('approves after a confirmation and then shows the decision instead of the controls', async () => {
    api.get.mockResolvedValue(pending())
    api.decide.mockResolvedValue(
      makeDetail(makeCompleteApplication({ status: 'APPROVED', editable: false, referenceId: 'TKO-1', decidedAt: '2026-09-21T09:00:00Z' })),
    )
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: 'Approve' }))
    expect(api.decide).not.toHaveBeenCalled() // nothing is sent until confirmed
    await user.click(screen.getByRole('button', { name: /yes, approve/i }))

    await waitFor(() => expect(api.decide).toHaveBeenCalledWith(7, { status: 'APPROVED', note: undefined }))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument())
    expect(screen.getAllByText('Approved').length).toBeGreaterThan(0)
  })

  it('will not reject without a reason', async () => {
    api.get.mockResolvedValue(pending())
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: 'Reject' }))

    expect(screen.getByText(/explain why the application is not approved/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /yes, reject/i })).not.toBeInTheDocument()
    expect(api.decide).not.toHaveBeenCalled()
  })

  it('rejects with the reviewer note and lets them back out at the confirmation', async () => {
    api.get.mockResolvedValue(pending())
    api.decide.mockResolvedValue(
      makeDetail(
        makeCompleteApplication({ status: 'REJECTED', referenceId: 'TKO-1', decidedAt: '2026-09-21T09:00:00Z', decisionNote: 'Licence photo is blurry.' }),
      ),
    )
    const user = userEvent.setup()
    renderDetail()

    await user.type(await screen.findByLabelText(/note to the driver/i), 'Licence photo is blurry.')
    await user.click(screen.getByRole('button', { name: 'Reject' }))
    await user.click(screen.getByRole('button', { name: /cancel/i }))
    expect(api.decide).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Reject' }))
    await user.click(screen.getByRole('button', { name: /yes, reject/i }))

    await waitFor(() => expect(api.decide).toHaveBeenCalledWith(7, { status: 'REJECTED', note: 'Licence photo is blurry.' }))
    expect(await screen.findByText('Licence photo is blurry.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('shows a server refusal and reloads when someone else already decided', async () => {
    api.get.mockResolvedValueOnce(pending())
    api.get.mockResolvedValueOnce(
      makeDetail(makeCompleteApplication({ status: 'APPROVED', editable: false, referenceId: 'TKO-1', decidedAt: '2026-09-21T09:00:00Z' })),
    )
    api.decide.mockRejectedValue(
      new ApiError({
        message: 'Only applications that are pending review can be approved or rejected.',
        status: 409,
        code: 'INVALID_STATUS_TRANSITION',
      }),
    )
    const user = userEvent.setup()
    renderDetail()

    await user.click(await screen.findByRole('button', { name: 'Approve' }))
    await user.click(screen.getByRole('button', { name: /yes, approve/i }))

    await waitFor(() => expect(api.get).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument())
  })

  it('does not offer decision controls for an application that is already decided', async () => {
    api.get.mockResolvedValue(
      makeDetail(makeCompleteApplication({ status: 'APPROVED', editable: false, referenceId: 'TKO-1', decidedAt: '2026-09-21T09:00:00Z' })),
    )
    renderDetail()

    await screen.findByRole('heading', { level: 1, name: 'Ada Lovelace' })
    expect(screen.queryByRole('button', { name: 'Approve' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Reject' })).not.toBeInTheDocument()
  })

  it('rejects a nonsense id without calling the server', () => {
    renderDetail('/admin/applications/abc')
    expect(screen.getByRole('alert')).toHaveTextContent(/not a valid application/i)
    expect(api.get).not.toHaveBeenCalled()
  })

  it('reports a missing application', async () => {
    api.get.mockRejectedValue(new ApiError({ message: 'Application not found.', status: 404, code: 'APPLICATION_NOT_FOUND' }))
    renderDetail()
    expect(await screen.findByRole('alert')).toHaveTextContent('Application not found.')
  })
})
