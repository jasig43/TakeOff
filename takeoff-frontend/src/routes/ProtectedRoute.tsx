import { Navigate, Outlet } from 'react-router-dom'
import type { Role } from '../api/types'
import { useAuth } from '../hooks/useAuth'

interface ProtectedRouteProps {
  /** The only role allowed to see the nested routes. */
  role: Role
}

const homePathFor = (role: Role) => (role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard')

/**
 * Client-side route guard. This is a UX convenience only: the backend independently
 * authenticates and authorizes every API call, so tampering with this check exposes no data.
 *
 * Signed-out visitors go to the sign-in page ("/"). A signed-in user with the wrong role is sent to
 * their own dashboard, never back to the sign-in page (which would bounce them again and loop).
 */
export function ProtectedRoute({ role }: ProtectedRouteProps) {
  const { user } = useAuth()

  if (!user) {
    return <Navigate to="/" replace />
  }
  if (user.role !== role) {
    return <Navigate to={homePathFor(user.role)} replace />
  }
  return <Outlet />
}
