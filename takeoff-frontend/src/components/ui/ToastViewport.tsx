import { AnimatePresence, motion } from 'framer-motion'
import { CircleAlert, CircleCheck, Info, X } from 'lucide-react'
import type { ToastItem, ToastKind } from '../../context/toast'

const styles: Record<ToastKind, { icon: typeof Info; accent: string; label: string }> = {
  success: { icon: CircleCheck, accent: 'text-success', label: 'Success' },
  error: { icon: CircleAlert, accent: 'text-danger', label: 'Error' },
  info: { icon: Info, accent: 'text-brand', label: 'Notice' },
}

interface ToastViewportProps {
  toasts: ToastItem[]
  onDismiss: (id: number) => void
}

export function ToastViewport({ toasts, onDismiss }: ToastViewportProps) {
  return (
    <div
      className="pointer-events-none fixed inset-x-0 bottom-0 z-50 flex flex-col items-center gap-2 p-4 sm:items-end sm:p-6"
      aria-live="polite"
      aria-atomic="false"
    >
      <AnimatePresence initial={false}>
        {toasts.map((toast) => {
          const { icon: Icon, accent, label } = styles[toast.kind]
          return (
            <motion.div
              key={toast.id}
              layout
              initial={{ opacity: 0, y: 16, scale: 0.98 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: 8, scale: 0.98 }}
              transition={{ duration: 0.2 }}
              role={toast.kind === 'error' ? 'alert' : 'status'}
              className="glass pointer-events-auto flex w-full max-w-sm items-start gap-3 rounded-2xl bg-surface-strong p-4"
            >
              <Icon className={`mt-0.5 h-5 w-5 shrink-0 ${accent}`} aria-hidden="true" />
              <p className="flex-1 text-sm leading-6 text-fg">
                <span className="sr-only">{label}: </span>
                {toast.message}
              </p>
              <button
                type="button"
                onClick={() => onDismiss(toast.id)}
                className="-m-1 rounded-lg p-1 text-muted transition hover:bg-brand-soft hover:text-fg"
                aria-label="Dismiss notification"
              >
                <X className="h-4 w-4" aria-hidden="true" />
              </button>
            </motion.div>
          )
        })}
      </AnimatePresence>
    </div>
  )
}
