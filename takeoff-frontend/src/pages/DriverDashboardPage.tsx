import { motion } from 'framer-motion'
import { ArrowRight, BadgeCheck, Bell, CircleCheck, Clock, Mail, Phone, RefreshCw } from 'lucide-react'
import { Link } from 'react-router-dom'
import { driverApi } from '../api/authApi'
import { applicationApi, notificationApi } from '../api/applicationApi'
import type { Application } from '../api/types'
import { AppShell } from '../components/layout/AppShell'
import { Button, ButtonLink } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Skeleton } from '../components/ui/Skeleton'
import { StatusBadge } from '../components/ui/StatusBadge'
import { useApiResource } from '../hooks/useApiResource'
import { usePageTitle } from '../hooks/usePageTitle'
import { formatDateTime } from '../utils/formatting'

interface Stage {
  label: string
  done: boolean
}

function buildStages(phoneVerified: boolean, application: Application): Stage[] {
  const { progress } = application
  return [
    { label: 'Account created', done: true },
    { label: 'Phone verified', done: phoneVerified },
    { label: 'Personal details', done: progress.personal },
    { label: 'Identity & licence', done: progress.identity },
    { label: 'Vehicle details', done: progress.vehicle },
    { label: 'Document uploads', done: progress.documents },
    { label: 'Submitted for review', done: application.status !== 'DRAFT' },
  ]
}

function nextAction(application: Application): { label: string; hint: string } {
  switch (application.status) {
    case 'DRAFT': {
      const started = application.progress.personal || application.progress.identity || application.progress.vehicle
      return started
        ? { label: 'Continue your application', hint: 'Pick up where you left off.' }
        : { label: 'Start your application', hint: 'It takes about ten minutes. You will need your documents to hand.' }
    }
    case 'PENDING_REVIEW':
      return { label: 'View submission', hint: 'Your application is with our review team.' }
    case 'APPROVED':
      return { label: 'View application', hint: 'You are approved.' }
    case 'REJECTED':
      return { label: 'Review feedback and resubmit', hint: 'Our team left a note explaining what to fix.' }
  }
}

export default function DriverDashboardPage() {
  usePageTitle('Driver dashboard')
  const profile = useApiResource(driverApi.getProfile, 'We could not load your profile.')
  const application = useApiResource(applicationApi.get, 'We could not load your application.')
  const inbox = useApiResource(notificationApi.inbox, 'We could not load your notifications.')

  const loading = (profile.loading && !profile.data) || (application.loading && !application.data)
  const error = profile.error || application.error
  const retry = () => {
    profile.reload()
    application.reload()
    inbox.reload()
  }

  const stages = profile.data && application.data ? buildStages(profile.data.phoneVerified, application.data) : []
  const completed = stages.filter((s) => s.done).length
  const percent = stages.length ? Math.round((completed / stages.length) * 100) : 0
  const action = application.data ? nextAction(application.data) : null
  const latest = inbox.data?.items.slice(0, 3) ?? []

  return (
    <AppShell>
      <div className="mx-auto max-w-4xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">
            {profile.data ? `Welcome, ${profile.data.fullName.split(' ')[0]}` : 'Your driver dashboard'}
          </h1>
          <p className="mt-2 text-muted">Track your onboarding application from here.</p>
        </header>

        {loading && (
          <div aria-busy="true" aria-label="Loading your dashboard" className="grid gap-4 md:grid-cols-2">
            <GlassCard className="space-y-4 p-6">
              <Skeleton className="h-6 w-1/2" />
              <Skeleton className="h-5 w-3/4" />
              <Skeleton className="h-5 w-2/3" />
            </GlassCard>
            <GlassCard className="space-y-4 p-6">
              <Skeleton className="h-6 w-1/3" />
              <Skeleton className="h-3 w-full" />
              <Skeleton className="h-5 w-1/2" />
            </GlassCard>
          </div>
        )}

        {!loading && error && (
          <GlassCard className="space-y-4 p-6" role="alert">
            <p className="font-medium text-danger">{error}</p>
            <Button variant="secondary" onClick={retry}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Try again
            </Button>
          </GlassCard>
        )}

        {!loading && !error && profile.data && application.data && action && (
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="grid gap-4 md:grid-cols-2">
            <GlassCard className="p-6 md:col-span-2">
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <h2 className="text-lg font-semibold">Application status</h2>
                  <div className="mt-3 flex flex-wrap items-center gap-3">
                    <StatusBadge status={application.data.status} />
                    {application.data.referenceId && (
                      <span className="text-sm text-muted">
                        Reference ID:{' '}
                        <span className="font-mono font-semibold text-fg">{application.data.referenceId}</span>
                      </span>
                    )}
                  </div>
                  <p className="mt-3 text-sm text-muted">{action.hint}</p>
                </div>
                <ButtonLink to="/driver/application" size="lg">
                  {action.label}
                  <ArrowRight className="h-4 w-4" aria-hidden="true" />
                </ButtonLink>
              </div>
            </GlassCard>

            <GlassCard className="p-6">
              <h2 className="text-lg font-semibold">Your profile</h2>
              <dl className="mt-4 space-y-3 text-sm">
                <div className="flex items-center gap-3">
                  <Mail className="h-4 w-4 text-muted" aria-hidden="true" />
                  <dt className="sr-only">Email</dt>
                  <dd className="break-all">{profile.data.email}</dd>
                </div>
                <div className="flex items-center gap-3">
                  <Phone className="h-4 w-4 text-muted" aria-hidden="true" />
                  <dt className="sr-only">Phone</dt>
                  <dd>{profile.data.phoneNumber}</dd>
                </div>
                <div className="flex items-center gap-3">
                  <BadgeCheck
                    className={`h-4 w-4 ${profile.data.phoneVerified ? 'text-success' : 'text-warning'}`}
                    aria-hidden="true"
                  />
                  <dt className="sr-only">Phone verification</dt>
                  <dd className={profile.data.phoneVerified ? 'font-medium text-success' : 'font-medium text-warning'}>
                    {profile.data.phoneVerified ? 'Phone verified' : 'Phone not verified'}
                  </dd>
                </div>
              </dl>
            </GlassCard>

            <GlassCard className="p-6">
              <h2 className="text-lg font-semibold">Onboarding progress</h2>
              <div
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={percent}
                aria-label="Onboarding progress"
                className="mt-4 h-3 overflow-hidden rounded-full bg-line"
              >
                <motion.div
                  className="h-full rounded-full bg-brand"
                  initial={{ width: 0 }}
                  animate={{ width: `${percent}%` }}
                  transition={{ duration: 0.8, ease: 'easeOut' }}
                />
              </div>
              <p className="mt-2 text-sm text-muted">
                {completed} of {stages.length} stages complete
              </p>
            </GlassCard>

            <GlassCard className="p-6 md:col-span-2">
              <h2 className="text-lg font-semibold">Application stages</h2>
              <ol className="mt-4 grid gap-3 sm:grid-cols-2">
                {stages.map((stage) => (
                  <li key={stage.label} className="flex items-center gap-3 text-sm">
                    {stage.done ? (
                      <CircleCheck className="h-5 w-5 text-success" aria-hidden="true" />
                    ) : (
                      <Clock className="h-5 w-5 text-muted" aria-hidden="true" />
                    )}
                    <span className={stage.done ? 'font-medium text-fg' : 'text-muted'}>{stage.label}</span>
                    <span className="ml-auto rounded-full bg-brand-soft px-2 py-0.5 text-xs font-semibold text-brand">
                      {stage.done ? 'Done' : 'To do'}
                    </span>
                  </li>
                ))}
              </ol>
            </GlassCard>

            <GlassCard className="p-6 md:col-span-2">
              <div className="flex items-center justify-between gap-3">
                <h2 className="flex items-center gap-2 text-lg font-semibold">
                  <Bell className="h-5 w-5 text-brand" aria-hidden="true" />
                  Latest notifications
                </h2>
                <Link to="/driver/notifications" className="text-sm font-semibold text-brand underline-offset-4 hover:underline">
                  View all
                </Link>
              </div>
              {latest.length === 0 ? (
                <p className="mt-4 text-sm text-muted">Nothing yet. We will let you know here when your application changes.</p>
              ) : (
                <ul className="mt-4 divide-y divide-line">
                  {latest.map((item) => (
                    <li key={item.id} className="py-3 first:pt-0 last:pb-0">
                      <p className={`text-sm ${item.read ? 'font-medium' : 'font-bold'}`}>
                        {!item.read && <span className="sr-only">Unread: </span>}
                        {item.title}
                      </p>
                      <p className="mt-0.5 text-sm text-muted">{item.message}</p>
                      <p className="mt-1 text-xs text-muted">{formatDateTime(item.createdAt)}</p>
                    </li>
                  ))}
                </ul>
              )}
            </GlassCard>
          </motion.div>
        )}
      </div>
    </AppShell>
  )
}
