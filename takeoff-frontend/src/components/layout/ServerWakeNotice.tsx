import { Loader2 } from 'lucide-react'
import { useServerWakeUp } from '../../hooks/useServerWakeUp'

/** A small notice at the top of the screen while the free demo server wakes from sleep. Renders nothing otherwise. */
export function ServerWakeNotice() {
  const waking = useServerWakeUp()
  if (!waking) return null

  return (
    <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex justify-center px-4">
      <div
        role="status"
        className="glass pointer-events-auto flex max-w-md items-start gap-3 rounded-2xl bg-surface-strong p-4"
      >
        <Loader2 className="mt-0.5 h-5 w-5 shrink-0 animate-spin text-brand motion-reduce:animate-none" aria-hidden="true" />
        <p className="text-sm leading-6 text-fg">
          <span className="font-semibold">Waking up the TakeOFF server.</span> The free demo host sleeps when idle, so
          the first request takes up to a minute. This message goes away when it is ready.
        </p>
      </div>
    </div>
  )
}
