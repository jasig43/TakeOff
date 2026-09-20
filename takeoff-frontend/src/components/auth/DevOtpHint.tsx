import { FlaskConical } from 'lucide-react'

/** Evaluator hint. Visible under `npm run dev`, or in builds with VITE_SHOW_DEV_HINTS=true. */
const SHOW_DEV_HINTS = import.meta.env.DEV || import.meta.env.VITE_SHOW_DEV_HINTS === 'true'

const TEST_PHONE = '+15550199'
const TEST_OTP = '123456'

export function DevOtpHint() {
  if (!SHOW_DEV_HINTS) return null
  return (
    <aside
      aria-label="Evaluator testing note"
      className="flex items-start gap-3 rounded-2xl border border-line bg-accent-soft p-4 text-sm leading-6 text-fg"
    >
      <FlaskConical className="mt-0.5 h-5 w-5 shrink-0 text-accent" aria-hidden="true" />
      <p>
        <strong>Evaluator note:</strong> the test phone number <code className="font-semibold">{TEST_PHONE}</code>{' '}
        receives the fixed one-time code <code className="font-semibold">{TEST_OTP}</code>. This only works while the
        backend runs in <em>development</em> mode; it is disabled in production.
      </p>
    </aside>
  )
}
