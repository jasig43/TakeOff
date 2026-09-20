import { useId, useState } from 'react'
import { ArrowLeft, Send } from 'lucide-react'
import { applicationApi } from '../../api/applicationApi'
import type { Application } from '../../api/types'
import { errorMessage } from '../../utils/apiHelpers'
import { Button } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'
import { ApplicationSummary } from './ApplicationSummary'

interface ReviewStepProps {
  application: Application
  onEdit: (stepIndex: number) => void
  onBack: () => void
  onSubmitted: (application: Application) => void
}

const SECTIONS: { key: 'personal' | 'identity' | 'vehicle' | 'documents'; label: string; step: number }[] = [
  { key: 'personal', label: 'Personal details', step: 0 },
  { key: 'identity', label: 'Identity and licence', step: 1 },
  { key: 'vehicle', label: 'Vehicle', step: 2 },
  { key: 'documents', label: 'Documents', step: 3 },
]

/** Summary of everything entered, with a final confirmation before the application is sent for review. */
export function ReviewStep({ application, onEdit, onBack, onSubmitted }: ReviewStepProps) {
  const checkboxId = useId()
  const [confirmed, setConfirmed] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')

  const incomplete = SECTIONS.filter((section) => !application.progress[section.key])
  const canSubmit = application.progress.readyToSubmit && confirmed

  const submit = async () => {
    setSubmitting(true)
    setError('')
    try {
      onSubmitted(await applicationApi.submit())
    } catch (failure) {
      setError(errorMessage(failure, 'We could not submit your application. Please try again.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <section aria-labelledby="review-heading" className="space-y-4">
      <div>
        <h2 id="review-heading" className="text-xl font-semibold">
          Review and submit
        </h2>
        <p className="mt-1 text-sm text-muted">
          Check everything below. Once you submit, our team reviews your application and you will be notified of the
          decision.
        </p>
      </div>

      {incomplete.length > 0 && (
        <div role="alert" className="rounded-2xl bg-warning-soft px-4 py-3 text-sm text-fg">
          <p className="font-semibold">Your application is not complete yet.</p>
          <ul className="mt-1 list-inside list-disc">
            {incomplete.map((section) => (
              <li key={section.key}>
                {section.label} -{' '}
                <button type="button" className="font-semibold underline" onClick={() => onEdit(section.step)}>
                  complete this step
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <ApplicationSummary application={application} loadDocument={applicationApi.documentBlob} onEdit={onEdit} />

      <GlassCard className="space-y-4 p-6">
        <div className="flex items-start gap-3">
          <input
            id={checkboxId}
            type="checkbox"
            checked={confirmed}
            onChange={(e) => setConfirmed(e.target.checked)}
            className="mt-1 h-5 w-5 shrink-0 accent-brand"
          />
          <label htmlFor={checkboxId} className="text-sm">
            I confirm that the information and documents I have provided are accurate and belong to me.
          </label>
        </div>
        {error && (
          <p role="alert" className="rounded-2xl bg-danger-soft px-4 py-3 text-sm font-medium text-danger">
            {error}
          </p>
        )}
        <div className="flex flex-col-reverse gap-3 sm:flex-row sm:justify-between">
          <Button variant="secondary" onClick={onBack} disabled={submitting}>
            <ArrowLeft className="h-4 w-4" aria-hidden="true" />
            Back
          </Button>
          <Button onClick={() => void submit()} disabled={!canSubmit} loading={submitting} loadingLabel="Submitting">
            <Send className="h-4 w-4" aria-hidden="true" />
            Submit application
          </Button>
        </div>
      </GlassCard>
    </section>
  )
}
