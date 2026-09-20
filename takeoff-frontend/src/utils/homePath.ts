import type { Role, UserSummary } from '../api/types'

/** The dashboard that belongs to a role. */
export const dashboardPathFor = (role: Role): string => (role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard')

/**
 * Where a signed-in person should be. Someone still on a temporary password an administrator issued goes to choose
 * their own first; everyone else goes to their dashboard.
 */
export const homePathFor = (user: Pick<UserSummary, 'role' | 'mustChangePassword'>): string =>
  user.mustChangePassword ? '/change-password' : dashboardPathFor(user.role)
