import { CircleCheck, CircleX, Clock, Copy, PenLine, type LucideIcon } from 'lucide-react'
import { applicationApi } from '../../api/applicationApi'
import type { Application, ApplicationStatus } from '../../api/types'
import { useToast } from '../../hooks/useToast'
import { formatDateTime } from '../../utils/formatting'
import { Button, ButtonLink } from '../ui/Button'
import { GlassCard } from '../ui/GlassCard'
import { StatusBadge } from '../ui/StatusBadge'
import { ApplicationSummary } from './ApplicationSummary'

const HEADLINE: Record<ApplicationStatus, { icon: LucideIcon; tone: string; title: string; body: string }> = {
  DRAFT: { icon: Clock, tone: 'text-brand', title: 'Application in progress', body: '' },
  PENDING_REVIEW: {
    icon: Clock,
    tone: 'text-accent',
    title: 'Your application is under review',
    body: 'Our team is checking your details and documents. You will get a notification as soon as there is a decision.',
  },
  APPROVED: {
    icon: CircleCheck,
    tone: 'text-success',
    title: 'You are approved',
    body: 'Congratulations, your application has been approved. Welcome to TakeOFF.',
  },
  REJECTED: {
    icon: CircleX,
    tone: 'text-danger',
    title: 'Your application was not approved',
    body: 'Please read the reviewer note below, correct what is needed and submit again.',
  },
}

interface SubmissionStatusProps {
  application: Application
  /** True right after the driver submits, to show the confirmation wording. */
  justSubmitted: boolean
  onEditAgain: () => void
}

/** What a driver sees once an application has been submitted: Reference ID, status, any decision, and the details. */
export function SubmissionStatus({ application, justSubmitted, onEditAgain }: SubmissionStatusProps) {
  const toast = useToast()
  const headline = HEADLINE[application.status]
  const Icon = headline.icon
  const title = justSubmitted && application.status === 'PENDING_REVIEW' ? 'Application submitted' : headline.title
  const body =
    justSubmitted && application.status === 'PENDING_REVIEW'
      ? 'Thank you. Your application is now waiting for review, and you will be notified as soon as there is a decision.'
      : headline.body

  const copyReference = async () => {
    if (!application.referenceId) return
    try {
      await navigator.clipboard.writeText(application.referenceId)
      toast.success('Reference ID copied.')
    } catch {
      toast.info('Copying is not available here. Please note the Reference ID down.')
    }
  }

  return (
    <div className="space-y-6">
      <GlassCard className="p-6 sm:p-8">
        <div className="flex items-start gap-4">
          <Icon className={`mt-1 h-8 w-8 shrink-0 ${headline.tone}`} aria-hidden="true" />
          <div>
            <h2 className="font-display text-2xl font-bold">{title}</h2>
            <p className="mt-2 text-muted">{body}</p>
          </div>
        </div>

        <dl className="mt-6 grid gap-4 sm:grid-cols-3">
          <div>
            <dt className="text-sm text-muted">Reference ID</dt>
            <dd className="mt-1 flex items-center gap-2">
              <span className="font-mono text-lg font-bold tracking-wide" data-testid="reference-id">
                {application.referenceId ?? '-'}
              </span>
              {application.referenceId && (
                <Button variant="ghost" onClick={() => void copyReference()} aria-label="Copy Reference ID">
                  <Copy className="h-4 w-4" aria-hidden="true" />
                </Button>
              )}
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted">Status</dt>
            <dd className="mt-1">
              <StatusBadge status={application.status} />
            </dd>
          </div>
          <div>
            <dt className="text-sm text-muted">Submitted</dt>
            <dd className="mt-1 font-medium">{formatDateTime(application.submittedAt)}</dd>
          </div>
        </dl>

        {application.decidedAt && (
          <p className="mt-4 text-sm text-muted">Decision recorded {formatDateTime(application.decidedAt)}.</p>
        )}

        {application.status === 'REJECTED' && application.decisionNote && (
          <div className="mt-4 rounded-2xl bg-danger-soft px-4 py-3">
            <p className="text-sm font-semibold text-danger">Reviewer note</p>
            <p className="mt-1 whitespace-pre-line text-sm text-fg">{application.decisionNote}</p>
          </div>
        )}

        <div className="mt-6 flex flex-wrap gap-3">
          {application.status === 'REJECTED' && application.editable && (
            <Button onClick={onEditAgain}>
              <PenLine className="h-4 w-4" aria-hidden="true" />
              Update and resubmit
            </Button>
          )}
          <ButtonLink to="/driver/dashboard" variant="secondary">
            Back to dashboard
          </ButtonLink>
        </div>
      </GlassCard>

      <div>
        <h2 className="mb-3 text-xl font-semibold">What you submitted</h2>
        <ApplicationSummary application={application} loadDocument={applicationApi.documentBlob} />
      </div>
    </div>
  )
}
