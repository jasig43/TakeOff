import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { MeshBackground } from './MeshBackground'
import { Navbar } from './Navbar'

interface PageShellProps {
  children: ReactNode
  /** Vertically center the content (used by the auth screens). */
  centered?: boolean
}

export function PageShell({ children, centered = false }: PageShellProps) {
  return (
    // overflow-x-clip (not -hidden): hidden makes this a scroll container, which silently breaks the sticky navbar.
    <div className="relative flex min-h-screen flex-col overflow-x-clip">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-xl focus:bg-brand focus:px-4 focus:py-2 focus:text-brand-fg"
      >
        Skip to main content
      </a>
      <MeshBackground />
      <Navbar />
      <main
        id="main"
        tabIndex={-1}
        className={`mx-auto w-full max-w-6xl flex-1 px-4 pb-16 pt-8 outline-none sm:px-6 ${centered ? 'flex items-center justify-center' : ''}`}
      >
        {children}
      </main>
      <footer className="mx-auto w-full max-w-6xl px-4 pb-8 text-sm text-muted sm:px-6">
        <div className="flex flex-col items-center justify-between gap-2 border-t border-line pt-6 sm:flex-row">
          <p>© {new Date().getFullYear()} TakeOFF. MVP Phase 1 preview.</p>
          <p className="flex gap-4">
            <Link to="/login" className="underline-offset-4 hover:underline">
              Driver sign in
            </Link>
            <Link to="/admin/login" className="underline-offset-4 hover:underline">
              Admin portal
            </Link>
          </p>
        </div>
      </footer>
    </div>
  )
}
