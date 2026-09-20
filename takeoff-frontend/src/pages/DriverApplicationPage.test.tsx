import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { applicationApi } from '../api/applicationApi'
import { ApiError } from '../api/client'
import { makeApplication, makeCompleteApplication, makeDocument } from '../test/fixtures'
import { renderPage } from '../test/renderPage'
import DriverApplicationPage from './DriverApplicationPage'

vi.mock('../api/applicationApi', () => ({
  applicationApi: {
    get: vi.fn(),
    savePersonal: vi.fn(),
    saveIdentity: vi.fn(),
    saveVehicle: vi.fn(),
    uploadDocument: vi.fn(),
    deleteDocument: vi.fn(),
    documentBlob: vi.fn(),
    submit: vi.fn(),
  },
  notificationApi: { inbox: vi.fn(), markRead: vi.fn(), markAllRead: vi.fn() },
}))

const api = vi.mocked(applicationApi)

function renderApplication() {
  return renderPage(<DriverApplicationPage />, { role: 'APPLICANT_DRIVER', route: '/driver/application' })
}

describe('driver application page', () => {
  beforeEach(async () => {
    const { notificationApi } = await import('../api/applicationApi')
    vi.mocked(notificationApi.inbox).mockResolvedValue({ items: [], unreadCount: 0 })
  })
  afterEach(() => {
    window.localStorage.clear()
    vi.resetAllMocks()
  })

  it('starts a new driver on the personal details step and validates before calling the server', async () => {
    api.get.mockResolvedValue(makeApplication())
    const user = userEvent.setup()
    renderApplication()

    expect(await screen.findByRole('heading', { name: 'Personal details' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /save and continue/i }))

    expect(screen.getByText('Date of birth is required.')).toBeInTheDocument()
    expect(screen.getByText('Address is required.')).toBeInTheDocument()
    expect(screen.getByText('Emergency contact name is required.')).toBeInTheDocument()
    expect(api.savePersonal).not.toHaveBeenCalled()
  })

  it('saves personal details, trims them, and moves on to the identity step', async () => {
    api.get.mockResolvedValue(makeApplication())
    api.savePersonal.mockResolvedValue(makeApplication({ progress: { personal: true, identity: false, vehicle: false, documents: false, readyToSubmit: false } }))
    const user = userEvent.setup()
    renderApplication()

    await screen.findByRole('heading', { name: 'Personal details' })
    fireEvent.change(screen.getByLabelText(/date of birth/i), { target: { value: '1990-05-14' } })
    await user.type(screen.getByLabelText(/^city or town/i), '  Harare ')
    await user.type(screen.getByLabelText(/^address/i), '12 Samora Machel Avenue')
    await user.type(screen.getByLabelText(/emergency contact name/i), 'Tendai Moyo')
    await user.type(screen.getByLabelText(/emergency contact phone/i), '+263771234567')
    await user.click(screen.getByRole('button', { name: /save and continue/i }))

    expect(api.savePersonal).toHaveBeenCalledWith({
      dateOfBirth: '1990-05-14',
      addressLine: '12 Samora Machel Avenue',
      city: 'Harare',
      emergencyContactName: 'Tendai Moyo',
      emergencyContactPhone: '+263771234567',
    })
    expect(await screen.findByRole('heading', { name: /identity and driver's licence/i })).toBeInTheDocument()
  })

  it('shows a duplicate national ID (409 with a code, no field list) under the ID field', async () => {
    api.get.mockResolvedValue(makeCompleteApplication({ progress: { personal: true, identity: false, vehicle: true, documents: true, readyToSubmit: false } }))
    api.saveIdentity.mockRejectedValue(
      new ApiError({ message: 'This national ID number is already registered.', status: 409, code: 'NATIONAL_ID_IN_USE' }),
    )
    const user = userEvent.setup()
    renderApplication()

    await screen.findByRole('heading', { name: /identity and driver's licence/i })
    await user.click(screen.getByRole('button', { name: /save and continue/i }))

    const message = await screen.findByText('This national ID number is already registered.')
    expect(screen.getByLabelText(/national id number/i)).toHaveAccessibleDescription(message.textContent ?? '')
    expect(screen.getByLabelText(/national id number/i)).toBeInvalid()
  })

  it('shows a duplicate plate number under the plate field', async () => {
    api.get.mockResolvedValue(makeCompleteApplication({ progress: { personal: true, identity: true, vehicle: false, documents: true, readyToSubmit: false } }))
    api.saveVehicle.mockRejectedValue(
      new ApiError({ message: 'This registration number is already registered.', status: 409, code: 'PLATE_IN_USE' }),
    )
    const user = userEvent.setup()
    renderApplication()

    await screen.findByRole('heading', { name: 'Vehicle registration' })
    await user.click(screen.getByRole('button', { name: /save and continue/i }))

    expect(await screen.findByText('This registration number is already registered.')).toBeInTheDocument()
    expect(screen.getByLabelText(/registration \(plate\) number/i)).toBeInvalid()
    expect(api.saveVehicle).toHaveBeenCalledWith({ vehicleType: 'PICKUP', plateNumber: 'ABC 1234', make: 'Toyota', model: 'Hilux' })
  })

  it('shows server validation messages next to the fields they belong to', async () => {
    api.get.mockResolvedValue(makeCompleteApplication({ progress: { personal: false, identity: true, vehicle: true, documents: true, readyToSubmit: false } }))
    api.savePersonal.mockRejectedValue(
      new ApiError({ message: 'Invalid', status: 400, code: 'VALIDATION_FAILED', fieldErrors: { city: ['City is not recognised.'] } }),
    )
    const user = userEvent.setup()
    renderApplication()

    await screen.findByRole('heading', { name: 'Personal details' })
    await user.click(screen.getByRole('button', { name: /save and continue/i }))

    expect(await screen.findByText('City is not recognised.')).toBeInTheDocument()
    expect(screen.getByLabelText(/^city or town/i)).toBeInvalid()
  })

  it('lets a driver upload a valid document and refuses an unsupported file without calling the server', async () => {
    const withoutDocuments = makeCompleteApplication({
      documents: [],
      progress: { personal: true, identity: true, vehicle: true, documents: false, readyToSubmit: false },
    })
    api.get.mockResolvedValue(withoutDocuments)
    api.uploadDocument.mockResolvedValue({ ...withoutDocuments, documents: [makeDocument('DRIVERS_LICENCE', 'licence.pdf')] })
    const user = userEvent.setup({ applyAccept: false })
    renderApplication()

    expect(await screen.findByRole('heading', { name: 'Documents' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /continue to review/i })).toBeDisabled()

    await user.upload(screen.getByLabelText("Upload Driver's licence"), new File(['x'], 'notes.txt', { type: 'text/plain' }))
    expect(await screen.findByText('Upload a PDF, JPG or PNG file.')).toBeInTheDocument()
    expect(api.uploadDocument).not.toHaveBeenCalled()

    const pdf = new File(['%PDF-1.4'], 'licence.pdf', { type: 'application/pdf' })
    await user.upload(screen.getByLabelText("Upload Driver's licence"), pdf)
    await waitFor(() => expect(api.uploadDocument).toHaveBeenCalledWith('DRIVERS_LICENCE', pdf))
    expect(await screen.findByText(/licence\.pdf/)).toBeInTheDocument()
    expect(screen.getByLabelText("Replace Driver's licence")).toBeInTheDocument()
  })

  it('opens on the review step when everything is filled in, and only submits after confirmation', async () => {
    api.get.mockResolvedValue(makeCompleteApplication())
    api.submit.mockResolvedValue(
      makeCompleteApplication({ status: 'PENDING_REVIEW', editable: false, referenceId: 'TKO-20260920-AB12CD', submittedAt: '2026-09-20T10:00:00Z' }),
    )
    const user = userEvent.setup()
    renderApplication()

    expect(await screen.findByRole('heading', { name: 'Review and submit' })).toBeInTheDocument()
    const submit = screen.getByRole('button', { name: /submit application/i })
    expect(submit).toBeDisabled()

    await user.click(screen.getByLabelText(/i confirm that the information/i))
    expect(submit).toBeEnabled()
    await user.click(submit)

    expect(await screen.findByRole('heading', { name: 'Application submitted' })).toBeInTheDocument()
    expect(screen.getByTestId('reference-id')).toHaveTextContent('TKO-20260920-AB12CD')
    expect(screen.getAllByText('Pending review').length).toBeGreaterThan(0)
    expect(screen.queryByRole('button', { name: /submit application/i })).not.toBeInTheDocument()
  })

  it('lists what is missing on the review step and disables submitting', async () => {
    api.get.mockResolvedValue(makeApplication())
    const user = userEvent.setup()
    renderApplication()

    await screen.findByRole('heading', { name: 'Personal details' })
    await user.click(screen.getByRole('button', { name: /^review/i }))

    expect(await screen.findByText(/your application is not complete yet/i)).toBeInTheDocument()
    await user.click(screen.getByLabelText(/i confirm that the information/i))
    expect(screen.getByRole('button', { name: /submit application/i })).toBeDisabled()
  })

  it('shows a submitted application read-only, with its Reference ID and status', async () => {
    api.get.mockResolvedValue(
      makeCompleteApplication({ status: 'PENDING_REVIEW', editable: false, referenceId: 'TKO-20260920-AB12CD', submittedAt: '2026-09-20T10:00:00Z' }),
    )
    renderApplication()

    expect(await screen.findByRole('heading', { name: /under review/i })).toBeInTheDocument()
    expect(screen.getByTestId('reference-id')).toHaveTextContent('TKO-20260920-AB12CD')
    expect(screen.queryByRole('button', { name: /save and continue/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /update and resubmit/i })).not.toBeInTheDocument()
    expect(screen.getByText('Harare')).toBeInTheDocument() // details are shown, but not editable
  })

  it('shows a rejection note and lets the driver reopen the application to fix it', async () => {
    api.get.mockResolvedValue(
      makeCompleteApplication({
        status: 'REJECTED',
        referenceId: 'TKO-20260920-AB12CD',
        decidedAt: '2026-09-21T10:00:00Z',
        decisionNote: 'The licence photo is blurry.',
      }),
    )
    const user = userEvent.setup()
    renderApplication()

    expect(await screen.findByRole('heading', { name: /not approved/i })).toBeInTheDocument()
    expect(screen.getByText('The licence photo is blurry.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /update and resubmit/i }))
    expect(await screen.findByRole('heading', { name: 'Personal details' })).toBeInTheDocument()
  })

  it('offers a retry when the application cannot be loaded', async () => {
    api.get.mockRejectedValueOnce(new ApiError({ message: 'Server is down', status: 500 }))
    api.get.mockResolvedValueOnce(makeApplication())
    const user = userEvent.setup()
    renderApplication()

    expect(await screen.findByRole('alert')).toHaveTextContent(/server is down|went wrong/i)
    await user.click(screen.getByRole('button', { name: /try again/i }))
    expect(await screen.findByRole('heading', { name: 'Personal details' })).toBeInTheDocument()
  })
})
