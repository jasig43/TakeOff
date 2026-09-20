/**
 * Token-storage strategy (Phase 1)
 * --------------------------------
 * The JWT access token, its expiry and the safe user summary are kept in `localStorage`
 * under a single key so a session survives page reloads. Expired sessions are discarded on read.
 *
 * Trade-off: any script running on this origin can read localStorage, so an XSS bug would expose
 * the token. This is acceptable for the MVP; hardening (short-lived access token + httpOnly
 * refresh cookie) is a planned next phase. The backend never trusts the client: every request is
 * re-validated and role-checked server-side.
 *
 * Pending OTP verification state (email, masked phone) lives in `sessionStorage` and never
 * contains a password or a token.
 */
import type { UserSummary } from '../api/types'

const SESSION_KEY = 'takeoff.auth'
const PENDING_KEY = 'takeoff.pendingVerification'

export interface StoredSession {
  accessToken: string
  /** Epoch milliseconds at which the token expires. */
  expiresAt: number
  user: UserSummary
}

export interface PendingVerification {
  email: string
  maskedPhone?: string
  /** Epoch milliseconds at which the currently issued OTP expires, when known. */
  otpExpiresAt?: number
}

function safeGet(storage: Storage | undefined, key: string): string | null {
  try {
    return storage?.getItem(key) ?? null
  } catch {
    return null
  }
}

function safeSet(storage: Storage | undefined, key: string, value: string): void {
  try {
    storage?.setItem(key, value)
  } catch {
    /* storage unavailable (private mode / quota): session simply won't persist */
  }
}

function safeRemove(storage: Storage | undefined, key: string): void {
  try {
    storage?.removeItem(key)
  } catch {
    /* ignore */
  }
}

const local = (): Storage | undefined => (typeof window === 'undefined' ? undefined : window.localStorage)
const session = (): Storage | undefined => (typeof window === 'undefined' ? undefined : window.sessionStorage)

export function readSession(now: number = Date.now()): StoredSession | null {
  const raw = safeGet(local(), SESSION_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<StoredSession>
    if (
      typeof parsed.accessToken !== 'string' ||
      typeof parsed.expiresAt !== 'number' ||
      !parsed.user ||
      typeof parsed.user.role !== 'string'
    ) {
      clearSession()
      return null
    }
    if (parsed.expiresAt <= now) {
      clearSession()
      return null
    }
    return parsed as StoredSession
  } catch {
    clearSession()
    return null
  }
}

export function saveSession(session: StoredSession): void {
  safeSet(local(), SESSION_KEY, JSON.stringify(session))
}

export function clearSession(): void {
  safeRemove(local(), SESSION_KEY)
}

export function getAccessToken(): string | null {
  return readSession()?.accessToken ?? null
}

export function readPendingVerification(): PendingVerification | null {
  const raw = safeGet(session(), PENDING_KEY)
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw) as Partial<PendingVerification>
    return typeof parsed.email === 'string' ? (parsed as PendingVerification) : null
  } catch {
    return null
  }
}

export function savePendingVerification(pending: PendingVerification): void {
  safeSet(session(), PENDING_KEY, JSON.stringify(pending))
}

export function clearPendingVerification(): void {
  safeRemove(session(), PENDING_KEY)
}
