import { useCallback, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ArrowLeft, BadgeCheck, Mail, Phone, RefreshCw, ShieldAlert } from 'lucide-react'
import { reviewApi } from '../api/applicationApi'
import type { ApplicationDetail, DocumentType } from '../api/types'
import { ApplicationSummary } from '../components/application/ApplicationSummary'
import { DecisionPanel } from '../components/application/DecisionPanel'
import { AppShell } from '../components/layout/AppShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Skeleton } from '../components/ui/Skeleton'
import { StatusBadge } from '../components/ui/StatusBadge'
import { useApiResource } from '../hooks/useApiResource'
import { useToast } from '../hooks/useToast'
import { usePageTitle } from '../hooks/usePageTitle'
import { formatDateTime } from '../utils/formatting'

const BackLink = () => (
  <Link to="/admin/applications" className="inline-flex items-center gap-1.5 text-sm font-semibold text-brand underline-offset-4 hover:underline">
    <ArrowLeft className="h-4 w-4" aria-hidden="true" />
    All applications
  </Link>
)

/** Validates the id before anything is fetched, so a mangled URL never reaches the API. */
export default function AdminApplicationDetailPage() {
  usePageTitle('Review application')
  const { id } = useParams()
  const applicationId = Number(id)

  if (!/^\d+$/.test(id ?? '') || !Number.isSafeInteger(applicationId) || applicationId <= 0) {
    return (
      <AppShell>
        <div className="mx-auto max-w-4xl space-y-4">
          <BackLink />
          <GlassCard className="flex items-center gap-3 p-6" role="alert">
            <ShieldAlert className="h-6 w-6 text-danger" aria-hidden="true" />
            <p className="font-medium">That is not a valid application.</p>
          </GlassCard>
        </div>
      </AppShell>
    )
  }
  return <ApplicationReview applicationId={applicationId} />
}

function ApplicationReview({ applicationId }: { applicationId: number }) {
  const toast = useToast()
  const fetchDetail = useCallback(() => reviewApi.get(applicationId), [applicationId])
  const { data, error, loading, reload } = useApiResource(fetchDetail, 'We could not load this application.')
  // The server's answer to a decision replaces the loaded copy.
  const [decided, setDecided] = useState<ApplicationDetail | null>(null)

  const detail = decided && decided.application.id === applicationId ? decided : data
  const loadDocument = useCallback((type: DocumentType) => reviewApi.documentBlob(applicationId, type), [applicationId])

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl space-y-6">
        <BackLink />

        {loading && !detail && (
          <div aria-busy="true" aria-label="Loading application" className="space-y-4">
            <Skeleton className="h-10 w-1/2" />
            <GlassCard className="space-y-4 p-6">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-5 w-2/3" />
            </GlassCard>
          </div>
        )}

        {!loading && error && !detail && (
          <GlassCard className="space-y-4 p-6" role="alert">
            <p className="font-medium text-danger">{error}</p>
            <Button variant="secondary" onClick={reload}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Try again
            </Button>
          </GlassCard>
        )}

        {detail && (
          <>
            <header className="flex flex-wrap items-start justify-between gap-4">
              <div>
                <h1 className="font-display text-3xl font-bold">{detail.driver.fullName}</h1>
                <p className="mt-2 text-muted">
                  Reference ID <span className="font-mono font-semibold text-fg">{detail.application.referenceId ?? '-'}</span>
                  , submitted {formatDateTime(detail.application.submittedAt)}
                </p>
              </div>
              <StatusBadge status={detail.application.status} />
            </header>

            <GlassCard className="p-6">
              <h2 className="text-lg font-semibold">Driver</h2>
              <dl className="mt-4 space-y-3 text-sm">
                <div className="flex items-center gap-3">
                  <Mail className="h-4 w-4 text-muted" aria-hidden="true" />
                  <dt className="sr-only">Email</dt>
                  <dd className="break-all">{detail.driver.email}</dd>
                </div>
                <div className="flex items-center gap-3">
                  <Phone className="h-4 w-4 text-muted" aria-hidden="true" />
                  <dt className="sr-only">Phone</dt>
                  <dd>{detail.driver.phoneNumber}</dd>
                </div>
                <div className="flex items-center gap-3">
                  <BadgeCheck
                    className={`h-4 w-4 ${detail.driver.phoneVerified ? 'text-success' : 'text-warning'}`}
                    aria-hidden="true"
                  />
                  <dt className="sr-only">Phone verification</dt>
                  <dd className={detail.driver.phoneVerified ? 'font-medium text-success' : 'font-medium text-warning'}>
                    {detail.driver.phoneVerified ? 'Phone verified' : 'Phone not verified'}
                  </dd>
                </div>
                <div className="text-muted">Registered {formatDateTime(detail.driver.registeredAt)}</div>
              </dl>
            </GlassCard>

            <ApplicationSummary application={detail.application} loadDocument={loadDocument} />

            {detail.application.status === 'PENDING_REVIEW' ? (
              <DecisionPanel
                applicationId={applicationId}
                onDecided={(next) => {
                  setDecided(next)
                  toast.success(
                    next.application.status === 'APPROVED' ? 'Application approved.' : 'Application not approved.',
                  )
                }}
                onConflict={reload}
              />
            ) : (
              <GlassCard className="p-6">
                <h2 className="text-lg font-semibold">Decision</h2>
                <div className="mt-3 flex flex-wrap items-center gap-3">
                  <StatusBadge status={detail.application.status} />
                  <span className="text-sm text-muted">{formatDateTime(detail.application.decidedAt)}</span>
                </div>
                {detail.application.decisionNote && (
                  <p className="mt-3 whitespace-pre-line text-sm">{detail.application.decisionNote}</p>
                )}
              </GlassCard>
            )}
          </>
        )}
      </div>
    </AppShell>
  )
}
