import type { ApplicationStatus, DocumentType, Role, VehicleType } from '../api/types'

export const STATUS_LABEL: Record<ApplicationStatus, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Pending review',
  APPROVED: 'Approved',
  REJECTED: 'Not approved',
}

export const ROLE_LABEL: Record<Role, string> = {
  APPLICANT_DRIVER: 'Driver',
  LOGISTICS_ADMIN: 'Administrator',
}

export const ROLES: Role[] = ['APPLICANT_DRIVER', 'LOGISTICS_ADMIN']

export const DOCUMENT_LABEL: Record<DocumentType, string> = {
  DRIVERS_LICENCE: "Driver's licence",
  VEHICLE_REGISTRATION: 'Vehicle registration',
  INSURANCE: 'Insurance certificate',
}

export const DOCUMENT_TYPES: DocumentType[] = ['DRIVERS_LICENCE', 'VEHICLE_REGISTRATION', 'INSURANCE']

export const VEHICLE_LABEL: Record<VehicleType, string> = {
  MOTORCYCLE: 'Motorcycle',
  CAR: 'Car',
  VAN: 'Van',
  PICKUP: 'Pickup',
  TRUCK: 'Truck',
}

export const VEHICLE_TYPES: VehicleType[] = ['MOTORCYCLE', 'CAR', 'VAN', 'PICKUP', 'TRUCK']

/** "1990-05-14" -> "14 May 1990". Falls back to the raw text if it isn't a date. */
export function formatDate(value: string | null | undefined): string {
  if (!value) return '-'
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : date.toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
