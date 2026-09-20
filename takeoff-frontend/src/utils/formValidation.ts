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

/**
 * Zimbabwean numbers are written "+263 77...", never "+263 077...": the local leading 0 is dropped in international
 * format. "+2630..." fits the E.164 shape but no SMS can be delivered to it, so it is refused with a clear message.
 */
const ZIMBABWE_LOCAL_ZERO = /^\+2630/

export function isValidPhone(value: string): boolean {
  const phone = normalizePhone(value)
  return PHONE_PATTERN.test(phone) && !ZIMBABWE_LOCAL_ZERO.test(phone)
}

/** Why a phone number is not acceptable for receiving a verification code, or undefined when it is fine. */
export function phoneProblem(value: string): string | undefined {
  const phone = normalizePhone(value)
  if (!PHONE_PATTERN.test(phone)) return 'Enter your phone number in international format, for example +15550199.'
  if (ZIMBABWE_LOCAL_ZERO.test(phone)) {
    return 'Zimbabwe numbers are written without the leading 0 after +263, for example +263771234567.'
  }
  return undefined
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
