import { Copy, KeyRound } from 'lucide-react'
import type { IssuedCredential } from '../../api/types'
import { useToast } from '../../hooks/useToast'
import { formatDateTime } from '../../utils/formatting'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'

interface TemporaryPasswordNoticeProps {
  credential: IssuedCredential
  /** What happened, for the heading: "Account created" or "New temporary password issued". */
  heading: string
  onDone: () => void
}

/**
 * Shows a freshly issued temporary password. This is the only time it is ever displayed: the server keeps only a hash,
 * and the parent drops it from state as soon as the administrator is done.
 */
export function TemporaryPasswordNotice({ credential, heading, onDone }: TemporaryPasswordNoticeProps) {
  const toast = useToast()
  const { user, temporaryPassword, temporaryPasswordExpiresAt } = credential

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(temporaryPassword)
      toast.success('Temporary password copied.')
    } catch {
      toast.info('Copying is not available here. Select the password and copy it by hand.')
    }
  }

  return (
    <GlassCard className="border-brand p-6" role="region" aria-label="Temporary password">
      <h3 className="flex items-center gap-2 text-lg font-semibold">
        <KeyRound className="h-5 w-5 text-brand" aria-hidden="true" />
        {heading}
      </h3>

      <dl className="mt-4 space-y-3 text-sm">
        <div className="grid gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-4">
          <dt className="text-muted">Name</dt>
          <dd className="font-medium">{user.fullName}</dd>
        </div>
        <div className="grid gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-4">
          <dt className="text-muted">Sign in with</dt>
          <dd className="break-all font-medium">{user.email}</dd>
        </div>
        <div className="grid gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-4">
          <dt className="text-muted">Temporary password</dt>
          <dd className="flex flex-wrap items-center gap-2">
            <code
              data-testid="temporary-password"
              className="select-all rounded-lg bg-brand-soft px-3 py-1.5 font-mono text-base font-bold tracking-wide"
            >
              {temporaryPassword}
            </code>
            <Button variant="secondary" onClick={() => void copy()} aria-label="Copy temporary password">
              <Copy className="h-4 w-4" aria-hidden="true" />
              Copy
            </Button>
          </dd>
        </div>
        <div className="grid gap-0.5 sm:grid-cols-[9rem_1fr] sm:gap-4">
          <dt className="text-muted">Works until</dt>
          <dd className="font-medium">{formatDateTime(temporaryPasswordExpiresAt)}</dd>
        </div>
      </dl>

      <p className="mt-4 rounded-2xl bg-warning-soft px-4 py-3 text-sm">
        <span className="font-semibold">This is the only time it is shown.</span> Give it to {user.fullName} privately.
        They will be asked to choose their own password the first time they sign in, and nothing else works until they do.
      </p>

      <div className="mt-4">
        <Button onClick={onDone}>Done</Button>
      </div>
    </GlassCard>
  )
}
