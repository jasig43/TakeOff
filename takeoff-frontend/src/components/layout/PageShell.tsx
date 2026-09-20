import type { ReactNode } from 'react'
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
      <footer className="mx-auto w-full max-w-6xl px-4 pb-8 text-center text-sm text-muted sm:px-6">
        <p>© {new Date().getFullYear()} TakeOFF</p>
      </footer>
    </div>
  )
}
