import { describe, expect, it } from 'vitest'
import {
  ageOn,
  fileExtension,
  validateDateOfBirth,
  validateDocumentFile,
  validateEmergencyPhone,
  validateLicenceClass,
  validateLicenceExpiry,
  validateLicenceNumber,
  validateNationalId,
  validatePlate,
  validateRequired,
} from './applicationValidation'

const NOW = new Date(2026, 8, 20) // 20 Sep 2026

describe('validateDateOfBirth', () => {
  it('requires a value', () => expect(validateDateOfBirth('', NOW)).toMatch(/required/i))
  it('rejects a malformed or impossible date', () => {
    expect(validateDateOfBirth('14/05/1990', NOW)).toMatch(/valid/i)
    expect(validateDateOfBirth('1990-02-31', NOW)).toMatch(/valid/i)
  })
  it('rejects a date that is not in the past', () => expect(validateDateOfBirth('2026-09-21', NOW)).toMatch(/past/i))
  it('rejects applicants under 18, up to the day before the birthday', () => {
    expect(validateDateOfBirth('2008-09-21', NOW)).toMatch(/18/)
    expect(validateDateOfBirth('2008-09-20', NOW)).toBeUndefined()
  })
  it('rejects implausibly old dates', () => expect(validateDateOfBirth('1900-01-01', NOW)).toMatch(/valid/i))
  it('accepts an ordinary adult', () => expect(validateDateOfBirth('1990-05-14', NOW)).toBeUndefined())
})

describe('ageOn', () => {
  it('counts whole years, not yet reached this year', () => {
    expect(ageOn(new Date(1990, 9, 1), NOW)).toBe(35)
    expect(ageOn(new Date(1990, 8, 20), NOW)).toBe(36)
  })
})

describe('validateRequired', () => {
  it('rejects blank and whitespace-only input', () => {
    expect(validateRequired('', 'City')).toBe('City is required.')
    expect(validateRequired('   ', 'City')).toBe('City is required.')
  })
  it('enforces the maximum length after trimming', () => {
    expect(validateRequired('a'.repeat(11), 'City', 10)).toMatch(/at most 10/)
    expect(validateRequired(`  ${'a'.repeat(10)}  `, 'City', 10)).toBeUndefined()
  })
})

describe('validateEmergencyPhone', () => {
  it('needs international format', () => {
    expect(validateEmergencyPhone('')).toMatch(/required/i)
    expect(validateEmergencyPhone('0771234567')).toMatch(/international/i)
    expect(validateEmergencyPhone('+263771234567')).toBeUndefined()
  })
})

describe('identity validators', () => {
  it('accepts a typical national ID and rejects junk', () => {
    expect(validateNationalId('63-123456 A 63')).toBeUndefined()
    expect(validateNationalId('12')).toMatch(/valid/i)
    expect(validateNationalId('12345 <script>')).toMatch(/valid/i)
    expect(validateNationalId('')).toMatch(/required/i)
  })
  it('accepts licence numbers and classes', () => {
    expect(validateLicenceNumber('DL123456')).toBeUndefined()
    expect(validateLicenceNumber('DL')).toMatch(/valid/i)
    expect(validateLicenceClass('4')).toBeUndefined()
    expect(validateLicenceClass('')).toMatch(/required/i)
    expect(validateLicenceClass('4;drop')).toMatch(/valid/i)
  })
  it('requires a licence that has not expired', () => {
    expect(validateLicenceExpiry('', NOW)).toMatch(/required/i)
    expect(validateLicenceExpiry('2026-09-19', NOW)).toMatch(/expired/i)
    expect(validateLicenceExpiry('2027-01-01', NOW)).toBeUndefined()
  })
})

describe('validatePlate', () => {
  it('accepts common plate formats', () => {
    expect(validatePlate('ABC 1234')).toBeUndefined()
    expect(validatePlate('AB-12')).toBeUndefined()
  })
  it('rejects blanks and symbols', () => {
    expect(validatePlate('')).toMatch(/required/i)
    expect(validatePlate('A')).toMatch(/valid/i)
    expect(validatePlate('AB$1234')).toMatch(/valid/i)
  })
})

describe('validateDocumentFile', () => {
  it('accepts PDF, JPG and PNG under the size limit, whatever the case', () => {
    for (const name of ['a.pdf', 'a.JPG', 'a.jpeg', 'scan.PNG']) {
      expect(validateDocumentFile({ name, size: 1000 })).toBeUndefined()
    }
  })
  it('rejects other types, empty files and oversize files', () => {
    expect(validateDocumentFile({ name: 'a.txt', size: 1000 })).toMatch(/PDF, JPG or PNG/)
    expect(validateDocumentFile({ name: 'noextension', size: 1000 })).toMatch(/PDF, JPG or PNG/)
    expect(validateDocumentFile({ name: 'a.pdf', size: 0 })).toMatch(/empty/i)
    expect(validateDocumentFile({ name: 'a.pdf', size: 5 * 1024 * 1024 + 1 })).toMatch(/too large/i)
    expect(validateDocumentFile({ name: 'a.pdf', size: 5 * 1024 * 1024 })).toBeUndefined()
  })
  it('reads the last extension', () => expect(fileExtension('a.b.PDF')).toBe('pdf'))
})
