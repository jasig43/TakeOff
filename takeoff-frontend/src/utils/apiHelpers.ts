import { isApiError } from '../api/client'

/** Field name -> a single message, from a server validation error (empty for any other kind of error). */
export function fieldMessages(error: unknown): Record<string, string> {
  if (!isApiError(error)) return {}
  const result: Record<string, string> = {}
  for (const [field, messages] of Object.entries(error.fieldErrors)) result[field] = messages.join(' ')
  return result
}

export function errorMessage(error: unknown, fallback: string): string {
  return isApiError(error) ? error.message : fallback
}

/**
 * Opens a protected file (one that needs the bearer token) in a new tab. The tab is opened synchronously inside the
 * click handler so popup blockers allow it, then pointed at the downloaded blob.
 */
export async function openProtectedFile(loader: () => Promise<Blob>): Promise<void> {
  const tab = window.open('', '_blank')
  try {
    const blob = await loader()
    const url = URL.createObjectURL(blob)
    if (tab) tab.location.href = url
    else window.location.assign(url)
    setTimeout(() => URL.revokeObjectURL(url), 60_000)
  } catch (error) {
    tab?.close()
    throw error
  }
}
