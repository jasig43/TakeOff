import { motion } from 'framer-motion'
import { Check, Circle } from 'lucide-react'
import type { PasswordRule } from '../../utils/passwordValidation'

interface PasswordChecklistProps {
  id: string
  rules: PasswordRule[]
}

/**
 * Live requirement checklist. State is conveyed with an icon AND text ("Met" / "Not met"),
 * never by color alone.
 */
export function PasswordChecklist({ id, rules }: PasswordChecklistProps) {
  return (
    <ul id={id} aria-label="Password requirements" className="space-y-1.5">
      {rules.map((rule) => (
        <li key={rule.id} className={`flex items-start gap-2 text-sm ${rule.met ? 'text-success' : 'text-muted'}`}>
          <span
            className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full border ${
              rule.met ? 'border-success bg-success text-brand-fg' : 'border-line'
            }`}
            aria-hidden="true"
          >
            {rule.met ? (
              <motion.span initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 500, damping: 22 }}>
                <Check className="h-3.5 w-3.5" strokeWidth={3} />
              </motion.span>
            ) : (
              <Circle className="h-2 w-2" />
            )}
          </span>
          <span>
            {rule.label}
            <span className="sr-only">{rule.met ? ': met' : ': not met'}</span>
          </span>
        </li>
      ))}
    </ul>
  )
}
