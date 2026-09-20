import { useState } from 'react'
import { RefreshCw } from 'lucide-react'
import { applicationApi } from '../api/applicationApi'
import type { Application } from '../api/types'
import { DocumentsStep } from '../components/application/DocumentsStep'
import { IdentityStep } from '../components/application/IdentityStep'
import { PersonalStep } from '../components/application/PersonalStep'
import { ReviewStep } from '../components/application/ReviewStep'
import { StepIndicator, type StepInfo } from '../components/application/StepIndicator'
import { SubmissionStatus } from '../components/application/SubmissionStatus'
import { VehicleStep } from '../components/application/VehicleStep'
import { AppShell } from '../components/layout/AppShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Skeleton } from '../components/ui/Skeleton'
import { useApiResource } from '../hooks/useApiResource'
import { NOTIFICATIONS_CHANGED_EVENT } from '../hooks/useUnreadNotifications'
import { usePageTitle } from '../hooks/usePageTitle'

const LAST_STEP = 4

function buildSteps(application: Application): StepInfo[] {
  const { progress } = application
  return [
    { id: 'personal', label: 'Personal', complete: progress.personal },
    { id: 'identity', label: 'Identity', complete: progress.identity },
    { id: 'vehicle', label: 'Vehicle', complete: progress.vehicle },
    { id: 'documents', label: 'Documents', complete: progress.documents },
    { id: 'review', label: 'Review', complete: false },
  ]
}

/** The first step that still needs attention, or the review step when everything is filled in. */
function firstIncompleteStep(application: Application): number {
  const index = buildSteps(application).findIndex((step) => !step.complete)
  return index < 0 || index > LAST_STEP ? LAST_STEP : index
}

export default function DriverApplicationPage() {
  usePageTitle('My application')
  const { data, error, loading, reload } = useApiResource(applicationApi.get, 'We could not load your application.')
  // Fresher copy after the driver saves or uploads; the loaded copy is used until then.
  const [latest, setLatest] = useState<Application | null>(null)
  const [step, setStep] = useState<number | null>(null)
  const [reopened, setReopened] = useState(false)
  const [justSubmitted, setJustSubmitted] = useState(false)

  const application = latest ?? data

  const changed = (next: Application) => setLatest(next)
  const advance = (next: Application, to: number) => {
    setLatest(next)
    setStep(to)
  }
  const submitted = (next: Application) => {
    setLatest(next)
    setReopened(false)
    setJustSubmitted(true)
    window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED_EVENT))
    window.scrollTo({ top: 0 })
  }

  // A rejected application is shown with the reviewer's note first; the driver chooses when to start editing.
  const showWizard = application !== null && (application.status === 'DRAFT' || (application.status === 'REJECTED' && reopened))
  const current = application ? (step ?? firstIncompleteStep(application)) : 0

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">My application</h1>
          <p className="mt-2 text-muted">
            {showWizard
              ? 'Complete each step. Your progress is saved as you go, so you can leave and come back.'
              : 'Your driver onboarding application.'}
          </p>
        </header>

        {loading && !application && (
          <div aria-busy="true" aria-label="Loading your application" className="space-y-4">
            <Skeleton className="h-14 w-full" />
            <GlassCard className="space-y-4 p-6">
              <Skeleton className="h-6 w-1/3" />
              <Skeleton className="h-11 w-full" />
              <Skeleton className="h-11 w-full" />
            </GlassCard>
          </div>
        )}

        {!loading && error && !application && (
          <GlassCard className="space-y-4 p-6" role="alert">
            <p className="font-medium text-danger">{error}</p>
            <Button variant="secondary" onClick={reload}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Try again
            </Button>
          </GlassCard>
        )}

        {application && showWizard && (
          <div className="space-y-6">
            <StepIndicator steps={buildSteps(application)} current={current} onSelect={setStep} />
            {current === 0 && <PersonalStep application={application} onSaved={(a) => advance(a, 1)} />}
            {current === 1 && (
              <IdentityStep application={application} onSaved={(a) => advance(a, 2)} onBack={() => setStep(0)} />
            )}
            {current === 2 && (
              <VehicleStep application={application} onSaved={(a) => advance(a, 3)} onBack={() => setStep(1)} />
            )}
            {current === 3 && (
              <DocumentsStep
                application={application}
                onChanged={changed}
                onBack={() => setStep(2)}
                onNext={() => setStep(LAST_STEP)}
              />
            )}
            {current === LAST_STEP && (
              <ReviewStep application={application} onEdit={setStep} onBack={() => setStep(3)} onSubmitted={submitted} />
            )}
          </div>
        )}

        {application && !showWizard && (
          <SubmissionStatus
            application={application}
            justSubmitted={justSubmitted}
            onEditAgain={() => {
              setReopened(true)
              setStep(0)
            }}
          />
        )}
      </div>
    </AppShell>
  )
}
