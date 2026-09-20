import { useCallback, useEffect, useState } from 'react'

interface CountdownState {
  /** Epoch ms at which the countdown reaches zero. */
  endAt: number
  /** Length of the current countdown in seconds (denominator for progress). */
  totalSeconds: number
}

/**
 * Wall-clock countdown. It derives the remaining time from a target timestamp instead of
 * decrementing a counter, so it stays accurate when the tab is throttled or backgrounded.
 */
export function useCountdown(initialSeconds: number) {
  const [state, setState] = useState<CountdownState>(() => ({
    endAt: Date.now() + initialSeconds * 1000,
    totalSeconds: initialSeconds,
  }))
  const [now, setNow] = useState(() => Date.now())

  const remainingMs = Math.max(0, state.endAt - now)
  const expired = remainingMs === 0

  useEffect(() => {
    if (expired) return
    const id = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(id)
  }, [expired, state.endAt])

  const restart = useCallback((seconds: number) => {
    const current = Date.now()
    setNow(current)
    setState({ endAt: current + seconds * 1000, totalSeconds: seconds })
  }, [])

  return {
    remainingSeconds: Math.ceil(remainingMs / 1000),
    /** 1 when just started, 0 when finished. */
    progress: state.totalSeconds > 0 ? remainingMs / (state.totalSeconds * 1000) : 0,
    expired,
    restart,
  }
}

export function formatClock(totalSeconds: number): string {
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
