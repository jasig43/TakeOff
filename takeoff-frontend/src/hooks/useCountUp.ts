import { useEffect, useState } from 'react'
import { useReducedMotion } from 'framer-motion'

interface CountUpOptions {
  /** Animation length in milliseconds. */
  duration?: number
  /** Hold at 0 until true (e.g. until the element scrolls into view). */
  enabled?: boolean
}

const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3)

/**
 * Animates an integer from 0 up to `target`. Honors the OS "reduce motion" preference
 * by returning the final value immediately.
 */
export function useCountUp(target: number, { duration = 1600, enabled = true }: CountUpOptions = {}): number {
  const reducedMotion = useReducedMotion()
  const [value, setValue] = useState(0)

  useEffect(() => {
    if (!enabled || reducedMotion) return

    let frame = 0
    // Anchor to the first frame's own timestamp: a rAF timestamp can precede performance.now(),
    // which would make the first frame's progress negative.
    let startedAt: number | undefined
    const tick = (now: number) => {
      startedAt ??= now
      const progress = Math.min(Math.max((now - startedAt) / duration, 0), 1)
      setValue(Math.round(target * easeOutCubic(progress)))
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, duration, enabled, reducedMotion])

  return reducedMotion ? target : value
}
