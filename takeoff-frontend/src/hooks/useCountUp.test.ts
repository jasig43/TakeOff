import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { useCountUp } from './useCountUp'

vi.mock('framer-motion', () => ({ useReducedMotion: vi.fn() }))
import { useReducedMotion } from 'framer-motion'

describe('useCountUp', () => {
  it('jumps straight to the target when the user prefers reduced motion', () => {
    vi.mocked(useReducedMotion).mockReturnValue(true)
    const { result } = renderHook(() => useCountUp(42))
    expect(result.current).toBe(42)
  })

  it('counts up from 0 to the target', async () => {
    vi.mocked(useReducedMotion).mockReturnValue(false)
    const { result } = renderHook(() => useCountUp(25, { duration: 60 }))
    expect(result.current).toBeLessThanOrEqual(25)
    await waitFor(() => expect(result.current).toBe(25))
  })

  it('stays at 0 until enabled', () => {
    vi.mocked(useReducedMotion).mockReturnValue(false)
    const { result } = renderHook(() => useCountUp(25, { enabled: false }))
    expect(result.current).toBe(0)
  })
})
