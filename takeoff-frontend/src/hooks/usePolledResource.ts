import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'

/** Fired by pages after they change something the header bell shows, so it refreshes immediately. */
export const NOTIFICATIONS_CHANGED_EVENT = 'takeoff:notifications-changed'

const REFRESH_MS = 60_000

/**
 * Keeps a small piece of data (the header bell's numbers) fresh: it loads on mount, on every navigation, when a page
 * fires {@link NOTIFICATIONS_CHANGED_EVENT}, and once a minute. `load` must be referentially stable (a module-level
 * function). Failures are ignored on purpose: a missing badge is better than an error in the header.
 * `replace` lets the caller put in a fresher copy it already has (for example the answer to "mark as read").
 */
export function usePolledResource<T>(enabled: boolean, load: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const { pathname } = useLocation()
  const [tick, setTick] = useState(0)

  useEffect(() => {
    if (!enabled) return
    const bump = () => setTick((n) => n + 1)
    window.addEventListener(NOTIFICATIONS_CHANGED_EVENT, bump)
    const interval = setInterval(bump, REFRESH_MS)
    return () => {
      window.removeEventListener(NOTIFICATIONS_CHANGED_EVENT, bump)
      clearInterval(interval)
    }
  }, [enabled])

  useEffect(() => {
    if (!enabled) return
    let cancelled = false
    load().then(
      (value) => {
        if (!cancelled) setData(value)
      },
      () => {
        /* ignore */
      },
    )
    return () => {
      cancelled = true
    }
  }, [enabled, load, pathname, tick])

  return { data: enabled ? data : null, replace: setData }
}
