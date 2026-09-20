import { useAuth } from '../../hooks/useAuth'
import { NotificationBell } from './NotificationBell'
import { ReviewBell } from './ReviewBell'

/**
 * The frosted-glass header across the top of every signed-in page (see `.glass-bar`). It is fixed, so it stays in view
 * while the page scrolls.
 * From the `lg` breakpoint it starts to the right of the sidebar; on small screens it spans the width and leaves room
 * for the floating menu button on the left (which shows the wordmark beside it instead of the sidebar's).
 */
export function TopBar() {
  const { user } = useAuth()
  if (!user) return null

  return (
    <header className="glass-bar fixed inset-x-0 top-0 z-20 flex h-16 items-center justify-between gap-3 pl-[4.5rem] pr-4 sm:pr-8 lg:left-64 lg:pl-8 lg:pr-10">
      <span className="font-display text-lg font-bold tracking-tight lg:hidden">
        Take<span className="text-brand">OFF</span>
      </span>
      <div className="ml-auto flex items-center gap-2">
        {user.role === 'APPLICANT_DRIVER' ? <NotificationBell /> : <ReviewBell />}
      </div>
    </header>
  )
}
