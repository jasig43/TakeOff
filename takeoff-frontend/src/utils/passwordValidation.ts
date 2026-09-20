/**
 * Password policy. The backend (`PasswordPolicy` / `@CompliantPassword`) is the authoritative
 * validator; this mirror gives users instant feedback and MUST be kept in sync with it.
 */
export const PASSWORD_MIN_LENGTH = 15
export const PASSWORD_SPECIAL_CHARACTERS = '!@#$%^&*()_+-=[]{}|;:,.<>?'

export type PasswordRuleId = 'length' | 'uppercase' | 'special'

export interface PasswordRule {
  id: PasswordRuleId
  /** Requirement as shown in the checklist. */
  label: string
  /** Short phrase used in "Missing: ..." status text. */
  missingLabel: string
  met: boolean
}

export type PasswordStrengthLevel = 'empty' | 'weak' | 'fair' | 'strong'

export interface PasswordEvaluation {
  rules: PasswordRule[]
  metCount: number
  isValid: boolean
  level: PasswordStrengthLevel
  /** Human-readable description for assistive technology. */
  summary: string
}

const specialSet = new Set(PASSWORD_SPECIAL_CHARACTERS)

export function hasUppercaseLetter(value: string): boolean {
  return /[A-Z]/.test(value)
}

export function hasSpecialCharacter(value: string): boolean {
  for (const char of value) {
    if (specialSet.has(char)) return true
  }
  return false
}

export function evaluatePassword(password: string): PasswordEvaluation {
  const rules: PasswordRule[] = [
    {
      id: 'length',
      label: `At least ${PASSWORD_MIN_LENGTH} characters`,
      missingLabel: `${PASSWORD_MIN_LENGTH} or more characters`,
      met: password.length >= PASSWORD_MIN_LENGTH,
    },
    {
      id: 'uppercase',
      label: 'One uppercase letter (A-Z)',
      missingLabel: 'an uppercase letter',
      met: hasUppercaseLetter(password),
    },
    {
      id: 'special',
      label: `One special character (${PASSWORD_SPECIAL_CHARACTERS})`,
      missingLabel: 'a special character',
      met: hasSpecialCharacter(password),
    },
  ]

  const metCount = rules.filter((rule) => rule.met).length
  const isValid = metCount === rules.length

  let level: PasswordStrengthLevel
  if (password.length === 0) level = 'empty'
  else if (isValid) level = 'strong'
  else if (metCount === 2) level = 'fair'
  else level = 'weak'

  return { rules, metCount, isValid, level, summary: describePassword(level, rules, metCount) }
}

function describePassword(level: PasswordStrengthLevel, rules: PasswordRule[], metCount: number): string {
  if (level === 'empty') {
    return `Password requirements: ${rules.map((rule) => rule.label).join(', ')}.`
  }
  const label = level === 'strong' ? 'Strong' : level === 'fair' ? 'Fair' : 'Weak'
  if (level === 'strong') {
    return `Password strength: ${label}. All ${rules.length} requirements met.`
  }
  const missing = rules.filter((rule) => !rule.met).map((rule) => rule.missingLabel)
  return `Password strength: ${label}. ${metCount} of ${rules.length} requirements met. Still needs ${missing.join(' and ')}.`
}
