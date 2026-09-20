import { motion } from 'framer-motion'
import type { PasswordEvaluation, PasswordStrengthLevel } from '../../utils/passwordValidation'

const LEVELS: Record<PasswordStrengthLevel, { label: string; bar: string; text: string; fill: number }> = {
  empty: { label: 'Not started', bar: 'bg-line', text: 'text-muted', fill: 0 },
  weak: { label: 'Weak', bar: 'bg-danger', text: 'text-danger', fill: 1 },
  fair: { label: 'Fair', bar: 'bg-warning', text: 'text-warning', fill: 2 },
  strong: { label: 'Strong', bar: 'bg-success', text: 'text-success', fill: 3 },
}

interface PasswordStrengthMeterProps {
  evaluation: PasswordEvaluation
  id: string
}

/** Three-segment meter that moves red -> orange -> green as requirements are met. */
export function PasswordStrengthMeter({ evaluation, id }: PasswordStrengthMeterProps) {
  const { label, bar, text, fill } = LEVELS[evaluation.level]

  return (
    <div id={id} className="space-y-2">
      <div className="flex items-center justify-between text-sm">
        <span className="font-medium text-fg">Password strength</span>
        <span className={`font-semibold ${text}`}>{label}</span>
      </div>
      <div className="flex gap-1.5" aria-hidden="true">
        {[1, 2, 3].map((segment) => (
          <div key={segment} className="h-2 flex-1 overflow-hidden rounded-full bg-line">
            <motion.div
              className={`h-full rounded-full ${bar}`}
              initial={false}
              animate={{ width: segment <= fill ? '100%' : '0%' }}
              transition={{ duration: 0.3, ease: 'easeOut' }}
            />
          </div>
        ))}
      </div>
      {/* Polite live region: announced when the strength changes, not on every keystroke. */}
      <p className="sr-only" role="status" aria-live="polite">
        {evaluation.summary}
      </p>
    </div>
  )
}
