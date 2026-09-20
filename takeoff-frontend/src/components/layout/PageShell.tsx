import type { ReactNode } from 'react'
import { MeshBackground } from './MeshBackground'

interface PageShellProps {
  children: ReactNode
  /** Vertically center the content (used by the auth screens). */
  centered?: boolean
}

/** Layout for public pages (sign in, sign up, OTP): no navigation, just the content on the backdrop. */
export function PageShell({ children, centered = false }: PageShellProps) {
  return (
    // overflow-x-clip (not -hidden): hidden makes this a scroll container, which can silently break sticky/fixed children.
    <div className="relative flex min-h-screen flex-col overflow-x-clip">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-xl focus:bg-brand focus:px-4 focus:py-2 focus:text-brand-fg"
      >
        Skip to main content
      </a>
      <MeshBackground />
      <main
        id="main"
        tabIndex={-1}
        className={`mx-auto w-full max-w-6xl flex-1 px-4 py-10 outline-none sm:px-6 ${centered ? 'flex items-center justify-center' : ''}`}
      >
        {children}
      </main>
    </div>
  )
}
