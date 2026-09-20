import { Link, useNavigate } from 'react-router-dom'
import { LayoutDashboard, LogOut, PlaneTakeoff } from 'lucide-react'
import { useAuth } from '../../hooks/useAuth'
import { ThemeToggle } from '../ui/ThemeToggle'
import { ButtonLink, Button } from '../ui/Button'

export function Navbar() {
  const { user, isAuthenticated, logout } = useAuth()
  const navigate = useNavigate()

  const dashboardPath = user?.role === 'LOGISTICS_ADMIN' ? '/admin/dashboard' : '/driver/dashboard'

  const handleLogout = () => {
    const loginPath = user?.role === 'LOGISTICS_ADMIN' ? '/admin/login' : '/login'
    logout()
    navigate(loginPath, { replace: true })
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

        {/*
          Secondary links are hidden on small screens by wrapping them, not by adding `hidden` to the
          button itself: the button already sets its own `display`, and the two utilities would collide.
          The hero and footer carry the same calls to action on phones.
        */}
        <div className="flex items-center gap-2 sm:gap-3">
          {isAuthenticated ? (
            <>
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
          ) : (
            <>
              <div className="hidden md:block">
                <ButtonLink to="/admin/login" variant="ghost">
                  Admin Portal
                </ButtonLink>
              </div>
              <ButtonLink to="/login" variant="ghost">
                Sign in
              </ButtonLink>
              <div className="hidden sm:block">
                <ButtonLink to="/register">Become a Driver</ButtonLink>
              </div>
            </>
          )}
          <ThemeToggle />
        </div>
      </nav>
    </header>
  )
}
