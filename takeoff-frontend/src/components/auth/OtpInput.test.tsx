import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { OtpInput } from './OtpInput'

/** Controlled harness that also exposes the current value for assertions. */
function Harness({ initial = '' }: { initial?: string }) {
  const [value, setValue] = useState(initial)
  return (
    <>
      <OtpInput value={value} onChange={setValue} />
      <output data-testid="value">{value}</output>
    </>
  )
}

const boxes = () => screen.getAllByRole('textbox') as HTMLInputElement[]
const current = () => screen.getByTestId('value').textContent

describe('OtpInput', () => {
  it('renders six labelled digit inputs in an accessible group', () => {
    render(<Harness />)
    expect(screen.getByRole('group', { name: /6-digit verification code/i })).toBeInTheDocument()
    expect(boxes()).toHaveLength(6)
    expect(screen.getByLabelText('Digit 1 of 6')).toBeInTheDocument()
    expect(screen.getByLabelText('Digit 6 of 6')).toBeInTheDocument()
  })

  it('accepts only numeric input and moves focus forward as digits are typed', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(boxes()[0])
    await user.keyboard('a')
    expect(current()).toBe('') // letters are ignored

    await user.keyboard('1')
    expect(current()).toBe('1')
    expect(boxes()[1]).toHaveFocus()

    await user.keyboard('23')
    expect(current()).toBe('123')
    expect(boxes()[3]).toHaveFocus()
  })

  it('moves focus back and clears the previous digit on Backspace in an empty box', async () => {
    const user = userEvent.setup()
    render(<Harness initial="12" />)

    await user.click(boxes()[2]) // empty third box
    await user.keyboard('{Backspace}')

    expect(current()).toBe('1')
    expect(boxes()[1]).toHaveFocus()
  })

  it('fills every box when a full six-digit code is pasted', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(boxes()[0])
    await user.paste('123456')

    expect(current()).toBe('123456')
    expect(boxes().map((box) => box.value)).toEqual(['1', '2', '3', '4', '5', '6'])
  })

  it('strips non-digits from pasted text and ignores excess digits', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(boxes()[0])
    await user.paste('12 34-56 78')

    expect(current()).toBe('123456')
  })

  it('supports arrow-key navigation between boxes', async () => {
    const user = userEvent.setup()
    render(<Harness initial="123456" />)

    await user.click(boxes()[2])
    await user.keyboard('{ArrowRight}')
    expect(boxes()[3]).toHaveFocus()
    await user.keyboard('{ArrowLeft}{ArrowLeft}')
    expect(boxes()[1]).toHaveFocus()
  })
})
