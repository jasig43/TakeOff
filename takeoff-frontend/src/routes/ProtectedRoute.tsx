import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../api/types'
import { useAuth } from '../hooks/useAuth'

interface ProtectedRouteProps {
  /** The only role allowed to see the nested routes. */
  role: Role
}

const loginPathFor = (role: Role) => (role === 'LOGISTICS_ADMIN' ? '/admin/login' : '/login')
const homePathFor = (role: Role) => (role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard')

/**
 * Client-side route guard. This is a UX convenience only: the backend independently
 * authenticates and authorizes every API call, so tampering with this check exposes no data.
 *
 * Unauthenticated visitors go to the login page for the route's own portal. Authenticated users
 * with the wrong role are sent to their own dashboard (never back to a login page, which would
 * bounce them again and create a redirect loop).
 */
export function ProtectedRoute({ role }: ProtectedRouteProps) {
  const { user } = useAuth()
  const location = useLocation()

  if (!user) {
    return <Navigate to={loginPathFor(role)} replace state={{ from: location.pathname }} />
  }
  if (user.role !== role) {
    return <Navigate to={homePathFor(user.role)} replace />
  }
  return <Outlet />
}
