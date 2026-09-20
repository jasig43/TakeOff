import { useEffect, useState } from 'react'
import { isApiError } from '../api/client'

interface ResourceState<T> {
  data: T | null
  error: string
  loading: boolean
}

/**
 * Loads a resource on mount and exposes loading / error / retry state.
 * `fetcher` must be a stable reference (e.g. a method on the api modules).
 * State is only set after the request settles, and results from superseded requests
 * (unmount, retry, React StrictMode's double-mount) are ignored.
 */
export function useApiResource<T>(fetcher: () => Promise<T>, fallbackError: string) {
  const [state, setState] = useState<ResourceState<T>>({ data: null, error: '', loading: true })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    let cancelled = false
    fetcher().then(
      (data) => {
        if (!cancelled) setState({ data, error: '', loading: false })
      },
      (error: unknown) => {
        if (!cancelled) setState({ data: null, error: isApiError(error) ? error.message : fallbackError, loading: false })
      },
    )
    return () => {
      cancelled = true
    }
  }, [fetcher, fallbackError, attempt])

  const reload = () => {
    setState((current) => ({ ...current, error: '', loading: true }))
    setAttempt((n) => n + 1)
  }

  return { ...state, reload }
}
