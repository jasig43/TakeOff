import { useRef, type ClipboardEvent, type KeyboardEvent } from 'react'

export const OTP_LENGTH = 6

interface OtpInputProps {
  /** Current code: 0-6 digits. */
  value: string
  onChange: (value: string) => void
  disabled?: boolean
  hasError?: boolean
  /** Id of an element describing the input (e.g. error text). */
  describedBy?: string
  autoFocus?: boolean
}

const digitsOnly = (input: string) => input.replace(/\D/g, '')

/**
 * Six separate single-digit inputs behaving like one field:
 * numeric-only, auto-advance, backspace moves back, arrow keys navigate,
 * and pasting a full code fills every box.
 */
export function OtpInput({ value, onChange, disabled = false, hasError = false, describedBy, autoFocus = false }: OtpInputProps) {
  const inputs = useRef<Array<HTMLInputElement | null>>([])

  const focusAt = (index: number) => {
    const target = inputs.current[Math.max(0, Math.min(OTP_LENGTH - 1, index))]
    target?.focus()
    target?.select()
  }

  const setDigit = (index: number, digit: string) => {
    const chars = value.padEnd(OTP_LENGTH, ' ').split('')
    chars[index] = digit || ' '
    onChange(chars.join('').replace(/ +$/, '').replace(/ /g, ''))
  }

  const handleChange = (index: number, raw: string) => {
    const digits = digitsOnly(raw)
    if (!digits) {
      setDigit(index, '')
      return
    }
    const existing = value[index]
    if (digits.length === 2 && existing) {
      // Typing over a filled box: keep the newly typed digit, drop the one that was already there.
      const typed = digits.replace(existing, '') || existing
      setDigit(index, typed)
      if (index < OTP_LENGTH - 1) focusAt(index + 1)
      return
    }
    if (digits.length > 1) {
      // Browser autofill (autocomplete="one-time-code") can deliver the whole code into one box.
      fillFrom(index, digits)
      return
    }
    setDigit(index, digits)
    if (index < OTP_LENGTH - 1) focusAt(index + 1)
  }

  const fillFrom = (start: number, digits: string) => {
    const chars = value.padEnd(OTP_LENGTH, ' ').split('')
    let cursor = start
    for (const digit of digits) {
      if (cursor >= OTP_LENGTH) break
      chars[cursor++] = digit
    }
    onChange(chars.join('').replace(/ +$/, '').replace(/ /g, ''))
    focusAt(Math.min(cursor, OTP_LENGTH - 1))
  }

  const handleKeyDown = (index: number, event: KeyboardEvent<HTMLInputElement>) => {
    switch (event.key) {
      case 'Backspace':
        if (!value[index] && index > 0) {
          event.preventDefault()
          setDigit(index - 1, '')
          focusAt(index - 1)
        }
        break
      case 'ArrowLeft':
        event.preventDefault()
        focusAt(index - 1)
        break
      case 'ArrowRight':
        event.preventDefault()
        focusAt(index + 1)
        break
      case 'Home':
        event.preventDefault()
        focusAt(0)
        break
      case 'End':
        event.preventDefault()
        focusAt(OTP_LENGTH - 1)
        break
    }
  }

  const handlePaste = (index: number, event: ClipboardEvent<HTMLInputElement>) => {
    event.preventDefault()
    const digits = digitsOnly(event.clipboardData.getData('text'))
    if (!digits) return
    // A full 6-digit paste always replaces the whole code, wherever the caret was.
    if (digits.length >= OTP_LENGTH) {
      onChange(digits.slice(0, OTP_LENGTH))
      focusAt(OTP_LENGTH - 1)
    } else {
      fillFrom(index, digits)
    }
  }

  return (
    <div role="group" aria-label="6-digit verification code" aria-describedby={describedBy} className="flex justify-center gap-2 sm:gap-3">
      {Array.from({ length: OTP_LENGTH }, (_, index) => (
        <input
          key={index}
          ref={(element) => {
            inputs.current[index] = element
          }}
          type="text"
          inputMode="numeric"
          pattern="[0-9]*"
          autoComplete={index === 0 ? 'one-time-code' : 'off'}
          maxLength={OTP_LENGTH}
          value={value[index] ?? ''}
          disabled={disabled}
          autoFocus={autoFocus && index === 0}
          aria-label={`Digit ${index + 1} of ${OTP_LENGTH}`}
          aria-invalid={hasError || undefined}
          onChange={(event) => handleChange(index, event.target.value)}
          onKeyDown={(event) => handleKeyDown(index, event)}
          onPaste={(event) => handlePaste(index, event)}
          onFocus={(event) => event.target.select()}
          className={`field h-14 w-11 px-0 text-center font-display text-2xl font-bold sm:h-16 sm:w-14 ${
            value[index] ? 'border-brand' : ''
          }`}
        />
      ))}
    </div>
  )
}
