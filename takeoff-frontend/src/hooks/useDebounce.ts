import { useEffect, useState } from 'react'

/** Returns `value` after it has stopped changing for `delayMs` (used so search boxes don't query on every keystroke). */
export function useDebounce<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}
