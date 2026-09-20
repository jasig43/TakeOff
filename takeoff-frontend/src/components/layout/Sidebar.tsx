import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { AnimatePresence, motion } from 'framer-motion'
import { ClipboardList, LayoutDashboard, LogOut, Menu, Settings, Users, X, type LucideIcon } from 'lucide-react'
import type { Role } from '../../api/types'
import { useAuth } from '../../hooks/useAuth'
import { Button } from '../ui/Button'

interface NavItem {
  to: string
  label: string
  icon: LucideIcon
}

/** Each role is only offered the destinations that belong to it. */
const NAV_ITEMS: Record<Role, NavItem[]> = {
  APPLICANT_DRIVER: [
    { to: '/driver/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/driver/application', label: 'My application', icon: ClipboardList },
  ],
  LOGISTICS_ADMIN: [
    { to: '/admin/dashboard', label: 'Dashboard', icon: LayoutDashboard },
    { to: '/admin/applications', label: 'Applications', icon: Users },
    { to: '/admin/settings', label: 'Settings', icon: Settings },
  ],
}

const ROLE_LABEL: Record<Role, string> = {
  APPLICANT_DRIVER: 'Driver applicant',
  LOGISTICS_ADMIN: 'Administrator',
}

function initials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0][0]
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  if (!user) return null

  const handleLogout = () => {
    logout()
    navigate('/', { replace: true })
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2.5 px-2 py-1">
        {/* Decorative: the wordmark beside it already names the app. */}
        <img src="/icon-192.png" alt="" width={40} height={40} className="h-10 w-10 rounded-xl shadow-md shadow-brand/30" />

        <span className="font-display text-xl font-bold tracking-tight">
          Take<span className="text-brand">OFF</span>
        </span>
      </div>

      <nav aria-label="Main" className="mt-8 flex-1 space-y-1">
        {NAV_ITEMS[user.role].map(({ to, label, icon: Icon }) => (
          <NavLink
            key={to}
            to={to}
            onClick={onNavigate}
            className={({ isActive }) =>
              `flex min-h-11 items-center gap-3 rounded-xl px-3 text-sm font-medium transition ${
                isActive ? 'bg-brand-soft font-semibold text-brand' : 'text-fg hover:bg-brand-soft'
              }`
            }
          >
            <Icon className="h-5 w-5" aria-hidden="true" />
            {label}
          </NavLink>
        ))}
      </nav>

      <div className="mt-6 space-y-3 border-t border-line pt-4">
        <div className="flex items-center gap-3 px-1">
          <span
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-soft text-sm font-bold text-brand"
            aria-hidden="true"
          >
            {initials(user.fullName)}
          </span>
          <div className="min-w-0">
            <p className="truncate text-sm font-semibold">{user.fullName}</p>
            <p className="truncate text-xs text-muted">{ROLE_LABEL[user.role]}</p>
          </div>
        </div>
        <Button variant="secondary" fullWidth onClick={handleLogout}>
          <LogOut className="h-4 w-4" aria-hidden="true" />
          Sign out
        </Button>
      </div>
    </div>
  )
}

/**
 * Left navigation for signed-in pages. From the `lg` breakpoint it is a fixed sidebar; on smaller screens it is
 * a slide-in drawer opened with a small floating menu button (there is deliberately no top bar).
 */
export function Sidebar() {
  const [open, setOpen] = useState(false)
  const menuButtonRef = useRef<HTMLButtonElement>(null)
  const closeButtonRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)

  const closeDrawer = () => {
    setOpen(false)
    menuButtonRef.current?.focus()
  }

  // While the drawer is open: Escape closes it, the page behind can't scroll, and focus starts inside it.
  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
        menuButtonRef.current?.focus()
      }
    }
    window.addEventListener('keydown', onKeyDown)
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    closeButtonRef.current?.focus()
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [open])

  // Keep Tab inside the open drawer.
  const trapFocus = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key !== 'Tab' || !panelRef.current) return
    const focusable = panelRef.current.querySelectorAll<HTMLElement>('a[href], button:not([disabled])')
    if (focusable.length === 0) return
    const first = focusable[0]
    const last = focusable[focusable.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }

  return (
    <>
      {/* Desktop: fixed sidebar */}
      <aside className="glass fixed inset-y-0 left-0 z-30 hidden w-64 rounded-none border-y-0 border-l-0 p-4 lg:block">
        <SidebarContent />
      </aside>

      {/* Mobile / tablet: floating menu button + drawer */}
      <button
        ref={menuButtonRef}
        type="button"
        onClick={() => setOpen(true)}
        className="glass fixed left-4 top-2.5 z-30 flex h-11 w-11 items-center justify-center rounded-2xl text-fg transition hover:bg-brand-soft active:scale-95 lg:hidden"
        aria-label="Open navigation menu"
        aria-haspopup="dialog"
        aria-expanded={open}
      >
        <Menu className="h-5 w-5" aria-hidden="true" />
      </button>

      <AnimatePresence>
        {open && (
          <div className="fixed inset-0 z-40 lg:hidden">
            <motion.div
              className="absolute inset-0 bg-black/50"
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              onClick={closeDrawer}
              aria-hidden="true"
            />
            <motion.div
              ref={panelRef}
              role="dialog"
              aria-modal="true"
              aria-label="Navigation menu"
              onKeyDown={trapFocus}
              className="glass absolute inset-y-0 left-0 w-72 max-w-[85vw] rounded-none border-y-0 border-l-0 bg-surface-strong p-4"
              initial={{ x: '-100%' }}
              animate={{ x: 0 }}
              exit={{ x: '-100%' }}
              transition={{ type: 'tween', duration: 0.22, ease: 'easeOut' }}
            >
              <button
                ref={closeButtonRef}
                type="button"
                onClick={closeDrawer}
                className="absolute right-3 top-3 flex h-10 w-10 items-center justify-center rounded-xl text-muted transition hover:bg-brand-soft hover:text-fg"
                aria-label="Close navigation menu"
              >
                <X className="h-5 w-5" aria-hidden="true" />
              </button>
              <SidebarContent onNavigate={() => setOpen(false)} />
            </motion.div>
          </div>
        )}
      </AnimatePresence>
    </>
  )
}
