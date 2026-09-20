import { useEffect, useState } from 'react'
import { isApiError } from '../api/client'

interface Settled<T> {
  /** Which request this result belongs to (fetcher identity + retry counter). */
  fetcher: unknown
  attempt: number
  data: T | null
  error: string
}

/**
 * Loads a resource and exposes loading / error / retry state.
 *
 * `fetcher` must be referentially stable between renders unless you WANT a reload: pass a module-level function, or
 * wrap it in `useCallback` with the filters it depends on. When `fetcher` changes, a new request starts and `loading`
 * is true until it settles, while the previous `data` stays available so lists don't blank out between filter changes.
 * Results from superseded requests (unmount, changed filters, retry, React StrictMode's double mount) are ignored.
 * State is only set after a request settles, never synchronously inside the effect.
 */
export function useApiResource<T>(fetcher: () => Promise<T>, fallbackError: string) {
  const [settled, setSettled] = useState<Settled<T> | null>(null)
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    fetcher().then(
      (data) => {
        if (!cancelled) setSettled({ fetcher, attempt, data, error: '' })
      },
      (error: unknown) => {
        if (!cancelled)
          setSettled({ fetcher, attempt, data: null, error: isApiError(error) ? error.message : fallbackError })
      },
    )
    return () => {
      cancelled = true
    }
  }, [fetcher, fallbackError, attempt])

  const loading = settled === null || settled.fetcher !== fetcher || settled.attempt !== attempt
  return {
    data: settled?.data ?? null,
    error: loading ? '' : (settled?.error ?? ''),
    loading,
    reload: () => setAttempt((n) => n + 1),
  }
}
