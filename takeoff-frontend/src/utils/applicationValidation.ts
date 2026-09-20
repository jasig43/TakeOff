/**
 * Client-side checks for the application forms. They mirror the backend's Bean Validation rules so users get instant
 * feedback; the backend remains authoritative and its per-field messages are shown if it disagrees.
 * Each function returns an error message, or `undefined` when the value is fine.
 */
import { isValidPhone } from './formValidation'

export const MINIMUM_AGE = 18
export const MAX_UPLOAD_BYTES = 5 * 1024 * 1024
export const ACCEPTED_EXTENSIONS = ['pdf', 'jpg', 'jpeg', 'png']
export const ACCEPT_ATTRIBUTE = '.pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png'

const NATIONAL_ID = /^[A-Za-z0-9][A-Za-z0-9 -]{4,18}[A-Za-z0-9]$/
const LICENCE_NUMBER = /^[A-Za-z0-9][A-Za-z0-9 /-]{2,28}[A-Za-z0-9]$/
const LICENCE_CLASS = /^[A-Za-z0-9 ,-]{1,20}$/
const PLATE = /^[A-Za-z0-9][A-Za-z0-9 -]{1,13}[A-Za-z0-9]$/

/** Today as YYYY-MM-DD in local time (what <input type="date"> uses). */
export function todayIso(now: Date = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function parseIsoDate(value: string): Date | null {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return null
  const [y, m, d] = value.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  return date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d ? date : null
}

/** Whole years between a birth date and `now`. */
export function ageOn(birth: Date, now: Date): number {
  let age = now.getFullYear() - birth.getFullYear()
  const birthdayPassed =
    now.getMonth() > birth.getMonth() || (now.getMonth() === birth.getMonth() && now.getDate() >= birth.getDate())
  if (!birthdayPassed) age -= 1
  return age
}

export function validateRequired(value: string, label: string, max = 200): string | undefined {
  const trimmed = value.trim()
  if (!trimmed) return `${label} is required.`
  if (trimmed.length > max) return `${label} must be at most ${max} characters.`
  return undefined
}

export function validateDateOfBirth(value: string, now: Date = new Date()): string | undefined {
  if (!value) return 'Date of birth is required.'
  const birth = parseIsoDate(value)
  if (!birth) return 'Enter a valid date of birth.'
  if (birth >= now) return 'Date of birth must be in the past.'
  const age = ageOn(birth, now)
  if (age < MINIMUM_AGE) return `You must be at least ${MINIMUM_AGE} years old to apply.`
  if (age > 100) return 'Enter a valid date of birth.'
  return undefined
}

export function validateEmergencyPhone(value: string): string | undefined {
  if (!value.trim()) return 'Emergency contact phone is required.'
  return isValidPhone(value) ? undefined : 'Use international format with the country code, for example +263771234567.'
}

export function validateNationalId(value: string): string | undefined {
  if (!value.trim()) return 'National ID number is required.'
  return NATIONAL_ID.test(value.trim()) ? undefined : 'Enter a valid national ID number (6 to 20 letters, digits, spaces or dashes).'
}

export function validateLicenceNumber(value: string): string | undefined {
  if (!value.trim()) return 'Licence number is required.'
  return LICENCE_NUMBER.test(value.trim()) ? undefined : 'Enter a valid licence number (4 to 30 letters, digits, spaces, dashes or slashes).'
}

export function validateLicenceClass(value: string): string | undefined {
  if (!value.trim()) return 'Licence class is required.'
  return LICENCE_CLASS.test(value.trim()) ? undefined : 'Enter a valid licence class, for example 4.'
}

export function validateLicenceExpiry(value: string, now: Date = new Date()): string | undefined {
  if (!value) return 'Licence expiry date is required.'
  const expiry = parseIsoDate(value)
  if (!expiry) return 'Enter a valid expiry date.'
  return expiry > now ? undefined : 'Your licence must not have expired.'
}

export function validatePlate(value: string): string | undefined {
  if (!value.trim()) return 'Registration (plate) number is required.'
  return PLATE.test(value.trim()) ? undefined : 'Enter a valid registration number (3 to 15 letters, digits, spaces or dashes).'
}

export function fileExtension(name: string): string {
  const dot = name.lastIndexOf('.')
  return dot < 0 ? '' : name.slice(dot + 1).toLowerCase()
}

/** Quick pre-check before uploading. The server still inspects the file's actual bytes. */
export function validateDocumentFile(file: { name: string; size: number }): string | undefined {
  if (file.size === 0) return 'That file is empty.'
  if (!ACCEPTED_EXTENSIONS.includes(fileExtension(file.name))) return 'Upload a PDF, JPG or PNG file.'
  if (file.size > MAX_UPLOAD_BYTES) return `The file is too large. The maximum size is ${MAX_UPLOAD_BYTES / (1024 * 1024)} MB.`
  return undefined
}
