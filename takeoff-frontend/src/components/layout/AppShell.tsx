import type { ReactNode } from 'react'
import { MeshBackground } from './MeshBackground'
import { Sidebar } from './Sidebar'

/** Layout for signed-in pages: left sidebar (drawer on small screens) and the page content beside it. */
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
      {/* pt-20 on small screens clears the floating menu button; lg:pl-72 leaves room for the 16rem sidebar. */}
      <main id="main" tabIndex={-1} className="min-h-screen px-4 pb-16 pt-20 outline-none sm:px-8 lg:pl-72 lg:pr-10 lg:pt-10">
        {children}
      </main>
    </div>
  )
}
