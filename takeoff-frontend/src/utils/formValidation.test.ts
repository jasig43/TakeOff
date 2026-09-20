import { describe, expect, it } from 'vitest'
import { isValidEmail, isValidFullName, isValidPhone, maskPhone, normalizePhone } from './formValidation'

describe('formValidation', () => {
  it('validates emails', () => {
    expect(isValidEmail('driver@example.com')).toBe(true)
    expect(isValidEmail('  driver@example.com  ')).toBe(true)
    expect(isValidEmail('driver@example')).toBe(false)
    expect(isValidEmail('not an email')).toBe(false)
  })

  it('normalizes and validates E.164 phone numbers, including the evaluator test number', () => {
    expect(normalizePhone('+1 (555) 019-9')).toBe('+15550199')
    expect(isValidPhone('+15550199')).toBe(true)
    expect(isValidPhone('+1 555 0199')).toBe(true)
    expect(isValidPhone('5550199')).toBe(false) // missing +country code
    expect(isValidPhone('+0123456789')).toBe(false) // country code cannot start with 0
    expect(isValidPhone('+12')).toBe(false)
  })

  it('validates full names by length', () => {
    expect(isValidFullName('Al')).toBe(true)
    expect(isValidFullName(' A ')).toBe(false)
    expect(isValidFullName('x'.repeat(101))).toBe(false)
  })

  it('masks all but the last four digits of a phone number', () => {
    expect(maskPhone('+15550199')).toBe('+••••0199')
  })
})
