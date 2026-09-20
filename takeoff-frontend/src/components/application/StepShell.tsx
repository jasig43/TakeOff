import type { FormEvent, ReactNode } from 'react'
import { ArrowLeft, ArrowRight } from 'lucide-react'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'

interface StepShellProps {
  title: string
  description: string
  children: ReactNode
  saving: boolean
  /** Message for a problem that isn't tied to one field (network failure, a conflict, ...). */
  formError?: string
  submitLabel?: string
  onSubmit: () => void
  onBack?: () => void
}

/** Common frame for a wizard step: heading, fields, a form-level error area, and Back / Save buttons. */
export function StepShell({
  title,
  description,
  children,
  saving,
  formError,
  submitLabel = 'Save and continue',
  onSubmit,
  onBack,
}: StepShellProps) {
  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    onSubmit()
  }

  return (
    <GlassCard className="p-6 sm:p-8">
      <h2 className="text-xl font-semibold">{title}</h2>
      <p className="mt-1 text-sm text-muted">{description}</p>

      <form onSubmit={handleSubmit} noValidate className="mt-6 space-y-5">
        {formError && (
          <p role="alert" className="rounded-2xl bg-danger-soft px-4 py-3 text-sm font-medium text-danger">
            {formError}
          </p>
        )}
        <div className="grid gap-5 sm:grid-cols-2">{children}</div>
        <div className="flex flex-col-reverse gap-3 pt-2 sm:flex-row sm:justify-between">
          {onBack ? (
            <Button variant="secondary" onClick={onBack} disabled={saving}>
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />
              Back
            </Button>
          ) : (
            <span />
          )}
          <Button type="submit" loading={saving} loadingLabel="Saving">
            {submitLabel}
            <ArrowRight className="h-4 w-4" aria-hidden="true" />
          </Button>
        </div>
      </form>
    </GlassCard>
  )
}
