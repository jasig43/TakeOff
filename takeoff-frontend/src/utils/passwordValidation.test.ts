import { describe, expect, it } from 'vitest'
import {
  PASSWORD_MIN_LENGTH,
  PASSWORD_SPECIAL_CHARACTERS,
  evaluatePassword,
  hasSpecialCharacter,
  hasUppercaseLetter,
} from './passwordValidation'

describe('evaluatePassword', () => {
  it('accepts a password with 15+ characters, an uppercase letter and a special character', () => {
    const result = evaluatePassword('CorrectHorse!Battery')
    expect(result.isValid).toBe(true)
    expect(result.level).toBe('strong')
    expect(result.rules.every((rule) => rule.met)).toBe(true)
  })

  it('accepts a password of exactly the minimum length', () => {
    const password = 'Abcdefghijklm!n' // 15 characters
    expect(password).toHaveLength(PASSWORD_MIN_LENGTH)
    expect(evaluatePassword(password).isValid).toBe(true)
  })

  it('rejects a password shorter than 15 characters', () => {
    const result = evaluatePassword('Abcdefghijkl!n') // 14 characters
    expect(result.isValid).toBe(false)
    expect(result.rules.find((r) => r.id === 'length')?.met).toBe(false)
    expect(result.rules.find((r) => r.id === 'uppercase')?.met).toBe(true)
    expect(result.rules.find((r) => r.id === 'special')?.met).toBe(true)
  })

  it('rejects a password without an uppercase letter', () => {
    const result = evaluatePassword('all lowercase but long!')
    expect(result.isValid).toBe(false)
    expect(result.rules.find((r) => r.id === 'uppercase')?.met).toBe(false)
  })

  it('rejects a password without a special character', () => {
    const result = evaluatePassword('NoSpecialCharactersHere123')
    expect(result.isValid).toBe(false)
    expect(result.rules.find((r) => r.id === 'special')?.met).toBe(false)
  })

  it('does not treat characters outside the allowed set (e.g. space, tilde, underscore-adjacent) as special', () => {
    expect(hasSpecialCharacter('Has Space And~Tilde')).toBe(false)
    expect(hasSpecialCharacter('quote"quote')).toBe(false)
  })

  it('accepts every character in the documented special-character set', () => {
    for (const char of PASSWORD_SPECIAL_CHARACTERS) {
      expect(hasSpecialCharacter(`abc${char}def`)).toBe(true)
    }
  })

  it('only counts ASCII A-Z as uppercase', () => {
    expect(hasUppercaseLetter('lower')).toBe(false)
    expect(hasUppercaseLetter('loweR')).toBe(true)
    expect(hasUppercaseLetter('Élan')).toBe(false)
  })

  it('maps requirements met to red / orange / green strength levels', () => {
    expect(evaluatePassword('').level).toBe('empty')
    expect(evaluatePassword('abc').level).toBe('weak') // 0 met
    expect(evaluatePassword('abcdefghijklmnopqrs').level).toBe('weak') // 1 met (length)
    expect(evaluatePassword('abcdefghijklmnopqrsT').level).toBe('fair') // 2 met
    expect(evaluatePassword('abcdefghijklmnopqrsT!').level).toBe('strong') // 3 met
  })

  it('produces accessible summary text naming what is missing', () => {
    const summary = evaluatePassword('short').summary
    expect(summary).toMatch(/Weak/)
    expect(summary).toMatch(/0 of 3 requirements met/)
    expect(summary).toMatch(/15 or more characters/)
    expect(summary).toMatch(/an uppercase letter/)
    expect(summary).toMatch(/a special character/)
  })
})
