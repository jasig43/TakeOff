import { useEffect, useState } from 'react'
import { apiClient, REQUEST_TIMEOUT_MS } from '../api/client'

const SLOW_AFTER_MS = 3_000
const RETRY_MS = 3_000

function isLocal(url: string): boolean {
  return /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:|\/|$)/.test(url)
}

async function isUp(url: string): Promise<boolean> {
  try {
    // Plain fetch, not apiClient: this must never attach a saved token or sign anyone out.
    return (await fetch(url, { cache: 'no-store' })).ok
  } catch {
    return false
  }
}

/**
 * True while a hosted API is still waking up. On load it asks the API's public health check, which also starts the
 * wake-up before anyone has typed a password; if there is no answer within a few seconds the visitor is told why
 * things are slow. It keeps asking until the API answers (or the request timeout has passed). It does nothing for a
 * local API, where a slow answer means something else.
 */
export function useServerWakeUp(): boolean {
  const [waking, setWaking] = useState(false)

  useEffect(() => {
    const baseUrl = apiClient.defaults.baseURL ?? ''
    if (!baseUrl || isLocal(baseUrl)) return

    let cancelled = false
    const slowTimer = setTimeout(() => {
      if (!cancelled) setWaking(true)
    }, SLOW_AFTER_MS)

    void (async () => {
      const deadline = Date.now() + REQUEST_TIMEOUT_MS
      while (!cancelled && Date.now() < deadline) {
        if (await isUp(`${baseUrl}/health`)) break
        await new Promise((resolve) => setTimeout(resolve, RETRY_MS))
      }
      clearTimeout(slowTimer)
      if (!cancelled) setWaking(false)
    })()

    return () => {
      cancelled = true
      clearTimeout(slowTimer)
    }
  }, [])

  return waking
}
