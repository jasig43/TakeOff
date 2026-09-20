import type { AdminUser, Application, ApplicationDetail, DocumentInfo, DocumentType } from '../api/types'

export function makeApplication(overrides: Partial<Application> = {}): Application {
  return {
    id: 7,
    referenceId: null,
    status: 'DRAFT',
    editable: true,
    personal: { dateOfBirth: null, addressLine: null, city: null, emergencyContactName: null, emergencyContactPhone: null },
    identity: { nationalId: null, licenceNumber: null, licenceClass: null, licenceExpiry: null },
    vehicle: { vehicleType: null, plateNumber: null, make: null, model: null },
    documents: [],
    progress: { personal: false, identity: false, vehicle: false, documents: false, readyToSubmit: false },
    submittedAt: null,
    decidedAt: null,
    decisionNote: null,
    ...overrides,
  }
}

export function makeDocument(type: DocumentType, filename = `${type.toLowerCase()}.pdf`): DocumentInfo {
  return { type, filename, contentType: 'application/pdf', sizeBytes: 2048, uploadedAt: '2026-09-01T09:30:00Z' }
}

/** Everything filled in and all three documents uploaded (still a draft unless overridden). */
export function makeCompleteApplication(overrides: Partial<Application> = {}): Application {
  return makeApplication({
    personal: {
      dateOfBirth: '1990-05-14',
      addressLine: '12 Samora Machel Avenue',
      city: 'Harare',
      emergencyContactName: 'Tendai Moyo',
      emergencyContactPhone: '+263771234567',
    },
    identity: { nationalId: '63-123456 A 63', licenceNumber: 'DL123456', licenceClass: '4', licenceExpiry: '2031-01-31' },
    vehicle: { vehicleType: 'PICKUP', plateNumber: 'ABC 1234', make: 'Toyota', model: 'Hilux' },
    documents: [makeDocument('DRIVERS_LICENCE'), makeDocument('VEHICLE_REGISTRATION'), makeDocument('INSURANCE')],
    progress: { personal: true, identity: true, vehicle: true, documents: true, readyToSubmit: true },
    ...overrides,
  })
}

export function makeUser(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    id: 2,
    fullName: 'Grace Hopper',
    email: 'grace@example.com',
    phoneNumber: '+263772222222',
    role: 'APPLICANT_DRIVER',
    phoneVerified: true,
    enabled: true,
    mustChangePassword: false,
    temporaryPasswordExpiresAt: null,
    createdAt: '2026-09-01T09:00:00Z',
    ...overrides,
  }
}

export function makeDetail(application: Application): ApplicationDetail {
  return {
    application,
    driver: {
      id: 3,
      fullName: 'Ada Lovelace',
      email: 'ada@example.com',
      phoneNumber: '+263771111111',
      phoneVerified: true,
      registeredAt: '2026-08-20T08:00:00Z',
    },
  }
}
