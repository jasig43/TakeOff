import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { Bell } from 'lucide-react'

interface BellPopoverProps {
  /** Accessible name of the bell button, including the count in words (the visual badge is hidden from assistive tech). */
  buttonLabel: string
  /** Accessible name of the panel. */
  panelLabel: string
  /** Number shown on the badge; nothing is shown for 0. */
  count: number
  /** Panel content. Call `close` from links so the panel closes when the user navigates. */
  children: (close: () => void) => ReactNode
}

/**
 * The header's bell: a button with a count badge that opens a small panel beneath it. It is a disclosure, not a
 * modal: Escape closes it and returns focus to the bell, and so does a click outside it.
 */
export function BellPopover({ buttonLabel, panelLabel, count, children }: BellPopoverProps) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const panelId = useId()

  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false)
        buttonRef.current?.focus()
      }
    }
    const onPointerDown = (event: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    document.addEventListener('mousedown', onPointerDown)
    return () => {
      document.removeEventListener('keydown', onKeyDown)
      document.removeEventListener('mousedown', onPointerDown)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        ref={buttonRef}
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-label={buttonLabel}
        aria-expanded={open}
        aria-controls={open ? panelId : undefined}
        className="relative flex h-11 w-11 items-center justify-center rounded-full text-fg transition hover:bg-brand-soft active:scale-95"
      >
        <Bell className="h-5 w-5" aria-hidden="true" />
        {count > 0 && (
          <span
            aria-hidden="true"
            className="absolute right-0.5 top-0.5 flex h-5 min-w-5 items-center justify-center rounded-full bg-brand px-1 text-[0.7rem] font-bold leading-none text-brand-fg"
          >
            {count > 99 ? '99+' : count}
          </span>
        )}
      </button>

      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-label={panelLabel}
          className="absolute right-0 top-full z-10 mt-2 max-h-[70vh] w-[min(24rem,calc(100vw-2rem))] overflow-y-auto rounded-2xl border border-line bg-surface-strong p-4 shadow-xl"
        >
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  )
}
