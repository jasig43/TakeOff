import type { ReactNode } from 'react'
import { MeshBackground } from './MeshBackground'
import { Sidebar } from './Sidebar'
import { TopBar } from './TopBar'

/**
 * Layout for signed-in pages: a left sidebar (a drawer on small screens), a fixed frosted-glass header with the notification
 * bell across the top, and the page content beside and below them.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="relative min-h-screen overflow-x-clip">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-xl focus:bg-brand focus:px-4 focus:py-2 focus:text-brand-fg"
      >
        Skip to main content
      </a>
      <MeshBackground />
      <Sidebar />
      <TopBar />
      {/* pt-24 clears the 4rem fixed header; lg:pl-72 leaves room for the 16rem sidebar. */}
      <main id="main" tabIndex={-1} className="min-h-screen px-4 pb-16 pt-24 outline-none sm:px-8 lg:pl-72 lg:pr-10">
        {children}
      </main>
    </div>
  )
}
