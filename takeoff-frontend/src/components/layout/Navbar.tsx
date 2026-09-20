import { Link, useNavigate } from 'react-router-dom'
import { LayoutDashboard, LogOut, PlaneTakeoff } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { ThemeToggle } from '../ui/ThemeToggle'
import { ButtonLink, Button } from '../ui/Button'

/** Minimal top bar: brand, theme toggle, and (only when signed in) Dashboard / Sign out. */
export function Navbar() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  const dashboardPath = user?.role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard'

  const handleLogout = () => {
    logout()
    navigate('/', { replace: true })
  }

  return (
    <header className="sticky top-0 z-40 px-4 pt-4 sm:px-6">
      <nav
        aria-label="Main"
        className="glass mx-auto flex max-w-6xl items-center justify-between gap-3 rounded-3xl px-4 py-2.5 sm:px-5"
      >
        <Link to="/" className="flex items-center gap-2.5 rounded-xl" aria-label="TakeOFF home">
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-brand text-brand-fg shadow-md shadow-brand/30">
            <PlaneTakeoff className="h-5 w-5" aria-hidden="true" />
          </span>
          <span className="font-display text-xl font-bold tracking-tight">
            Take<span className="text-brand">OFF</span>
          </span>
        </Link>

        <div className="flex items-center gap-2 sm:gap-3">
          {isAuthenticated && (
            <>
              {/* Wrapped, not given `hidden` directly: the button sets its own display and the two would collide. */}
              <div className="hidden sm:block">
                <ButtonLink to={dashboardPath} variant="ghost">
                  <LayoutDashboard className="h-4 w-4" aria-hidden="true" />
                  Dashboard
                </ButtonLink>
              </div>
              <Button variant="secondary" onClick={handleLogout}>
                <LogOut className="h-4 w-4" aria-hidden="true" />
                Sign out
              </Button>
            </>
          )}
          <ThemeToggle />
        </div>
      </nav>
    </header>
  )
}
