const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/
/** E.164: "+" then a non-zero digit followed by 6 to 14 more digits. Mirrors the backend pattern. */
const PHONE_PATTERN = /^\+[1-9]\d{6,14}$/

export function isValidEmail(value: string): boolean {
  return value.length <= 254 && EMAIL_PATTERN.test(value.trim())
}

/** Strips the spaces, dashes, dots and parentheses people naturally type in phone numbers. */
export function normalizePhone(value: string): string {
  return value.replace(/[\s\-().]/g, '')
}

export function isValidPhone(value: string): boolean {
  return PHONE_PATTERN.test(normalizePhone(value))
}

export function isValidFullName(value: string): boolean {
  const trimmed = value.trim()
  return trimmed.length >= 2 && trimmed.length <= 100
}

/** Masks all but the last 4 digits, e.g. "+15550199" -> "+•••••0199". */
export function maskPhone(phone: string): string {
  if (phone.length <= 5) return phone
  return `${phone.slice(0, 1)}${'•'.repeat(phone.length - 5)}${phone.slice(-4)}`
}
