import { useState, type PointerEvent } from 'react'
import { AnimatePresence, motion } from 'framer-motion'
import { useToast } from '../../hooks/useToast'

interface Ripple {
  id: number
  x: number
  y: number
}

interface ProviderButtonProps {
  monogram: string
  label: string
  onPress: () => void
}

function ProviderButton({ monogram, label, onPress }: ProviderButtonProps) {
  const [ripples, setRipples] = useState<Ripple[]>([])

  const handlePointerDown = (event: PointerEvent<HTMLButtonElement>) => {
    const rect = event.currentTarget.getBoundingClientRect()
    const ripple: Ripple = { id: Date.now() + Math.random(), x: event.clientX - rect.left, y: event.clientY - rect.top }
    setRipples((current) => [...current, ripple])
  }

  return (
    <motion.button
      type="button"
      whileTap={{ scale: 0.97 }}
      onPointerDown={handlePointerDown}
      onClick={onPress}
      className="glass relative flex min-h-12 w-full items-center justify-center gap-3 overflow-hidden rounded-2xl px-4 py-3 text-sm font-semibold text-fg transition hover:bg-brand-soft"
    >
      <span
        aria-hidden="true"
        className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-soft text-sm font-bold text-brand"
      >
        {monogram}
      </span>
      <span>{label}</span>
      <span className="rounded-full bg-accent-soft px-2 py-0.5 text-xs font-semibold text-accent">Coming soon</span>
      <AnimatePresence>
        {ripples.map((ripple) => (
          <motion.span
            key={ripple.id}
            aria-hidden="true"
            className="pointer-events-none absolute h-24 w-24 rounded-full bg-brand/30"
            style={{ left: ripple.x - 48, top: ripple.y - 48 }}
            initial={{ scale: 0, opacity: 0.6 }}
            animate={{ scale: 3, opacity: 0 }}
            transition={{ duration: 0.6, ease: 'easeOut' }}
            onAnimationComplete={() => setRipples((current) => current.filter((r) => r.id !== ripple.id))}
          />
        ))}
      </AnimatePresence>
    </motion.button>
  )
}

/**
 * Phase 1 mock: these buttons do NOT start an OAuth flow, request tokens, or authenticate anyone.
 * They exist to preview the planned sign-in options and say so. They use neutral monograms
 * rather than the providers' trademarked logos.
 */
export function OAuthButtons() {
  const toast = useToast()
  const comingSoon = (provider: string) => () =>
    toast.info(`Sign in with ${provider} is a demo button in Phase 1 and isn't connected yet. Use email and password.`)

  return (
    <div className="space-y-3">
      <div className="flex items-center gap-3 text-sm text-muted" aria-hidden="true">
        <span className="h-px flex-1 bg-line" />
        or continue with (demo)
        <span className="h-px flex-1 bg-line" />
      </div>
      <p className="sr-only">Social sign-in options are demo buttons and are not available yet.</p>
      <div className="grid gap-3 sm:grid-cols-2">
        <ProviderButton monogram="G" label="Sign in with Google" onPress={comingSoon('Google')} />
        <ProviderButton monogram="A" label="Sign in with Apple" onPress={comingSoon('Apple')} />
      </div>
    </div>
  )
}
