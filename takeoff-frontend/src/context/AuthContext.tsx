import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { UNAUTHORIZED_EVENT } from '../api/client'
import type { JwtResponse, Role } from '../api/types'
import {
  clearPendingVerification,
  clearSession,
  readSession,
  saveSession,
  type StoredSession,
} from '../utils/tokenStorage'
import { AuthContext, type AuthContextValue } from './auth'

/** setTimeout stores its delay in a signed 32-bit int; anything larger fires immediately. */
const MAX_TIMEOUT_MS = 2_147_483_647

export function AuthProvider({ children }: { children: ReactNode }) {
  // readSession() discards expired sessions, so a stale token never counts as "logged in".
  const [session, setSession] = useState<StoredSession | null>(() => readSession())

  const login = useCallback((response: JwtResponse) => {
    const next: StoredSession = {
      accessToken: response.accessToken,
      expiresAt: Date.now() + response.expiresInSeconds * 1000,
      user: response.user,
    }
    saveSession(next)
    clearPendingVerification()
    setSession(next)
  }, [])

  const logout = useCallback(() => {
    clearSession()
    clearPendingVerification()
    setSession(null)
  }, [])

  // Sign out automatically when the token expires. This keeps in-memory state consistent with
  // storage, which is what prevents login <-> dashboard redirect loops on an expired session.
  const expiresAt = session?.expiresAt
  useEffect(() => {
    if (expiresAt === undefined) return
    const timer = setTimeout(() => {
      clearSession()
      setSession(null)
    }, Math.min(Math.max(expiresAt - Date.now(), 0), MAX_TIMEOUT_MS))
    return () => clearTimeout(timer)
  }, [expiresAt])

  // The API client fires this when the server rejects our token (expired or revoked).
  useEffect(() => {
    const onUnauthorized = () => setSession(null)
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
  }, [])

  // Keep multiple tabs in sync: signing out (or in) in one tab updates the others.
  useEffect(() => {
    const onStorage = (event: StorageEvent) => {
      if (event.key === 'takeoff.auth') setSession(readSession())
    }
    window.addEventListener('storage', onStorage)
    return () => window.removeEventListener('storage', onStorage)
  }, [])

  const user = session?.user ?? null
  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      login,
      logout,
      hasRole: (role: Role) => user?.role === role,
    }),
    [user, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
