import { createContext } from 'react'
import type { JwtResponse, Role, UserSummary } from '../api/types'

export interface AuthContextValue {
  user: UserSummary | null
  isAuthenticated: boolean
  /** Persists a successful login / OTP-verification response and updates app state. */
  login: (response: JwtResponse) => void
  logout: () => void
  hasRole: (role: Role) => boolean
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined)
