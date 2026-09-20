import { motion } from 'framer-motion'
import { BadgeCheck, CircleCheck, Clock, Mail, Phone, RefreshCw } from 'lucide-react'
import { driverApi } from '../api/authApi'
import { PageShell } from '../components/layout/PageShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Skeleton } from '../components/ui/Skeleton'
import { useApiResource } from '../hooks/useApiResource'
import { usePageTitle } from '../hooks/usePageTitle'

const STAGES = [
  'Account created',
  'Phone verified',
  'Personal details',
  'Identity & licence',
  'Vehicle details',
  'Document uploads',
  'Review & submit',
]

export default function DriverDashboardPage() {
  usePageTitle('Driver dashboard')
  const { data: profile, error, loading, reload } = useApiResource(driverApi.getProfile, 'We could not load your profile.')

  const completed = profile ? (profile.phoneVerified ? 2 : 1) : 0
  const percent = Math.round((completed / STAGES.length) * 100)

  return (
    <PageShell>
      <div className="mx-auto max-w-4xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">
            {profile ? `Welcome, ${profile.fullName.split(' ')[0]}` : 'Your driver dashboard'}
          </h1>
          <p className="mt-2 text-muted">Track your onboarding application. More steps unlock in upcoming releases.</p>
        </header>

        {loading && (
          <div aria-busy="true" aria-label="Loading your profile" className="grid gap-4 md:grid-cols-2">
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
            <Button variant="secondary" onClick={reload}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Try again
            </Button>
          </GlassCard>
        )}

        {!loading && profile && (
          <motion.div initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} className="grid gap-4 md:grid-cols-2">
            <GlassCard className="p-6">
              <h2 className="text-lg font-semibold">Your profile</h2>
              <dl className="mt-4 space-y-3 text-sm">
                <div className="flex items-center gap-3">
                  <Mail className="h-4 w-4 text-muted" aria-hidden="true" />
                  <dt className="sr-only">Email</dt>
                  <dd>{profile.email}</dd>
                </div>
                <div className="flex items-center gap-3">
                  <Phone className="h-4 w-4 text-muted" aria-hidden="true" />
                  <dt className="sr-only">Phone</dt>
                  <dd>{profile.phoneNumber}</dd>
                </div>
                <div className="flex items-center gap-3">
                  <BadgeCheck className={`h-4 w-4 ${profile.phoneVerified ? 'text-success' : 'text-warning'}`} aria-hidden="true" />
                  <dt className="sr-only">Phone verification</dt>
                  <dd className={profile.phoneVerified ? 'font-medium text-success' : 'font-medium text-warning'}>
                    {profile.phoneVerified ? 'Phone verified' : 'Phone not verified'}
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
                {completed} of {STAGES.length} stages complete
              </p>
            </GlassCard>

            <GlassCard className="p-6 md:col-span-2">
              <h2 className="text-lg font-semibold">Application stages</h2>
              <ol className="mt-4 grid gap-3 sm:grid-cols-2">
                {STAGES.map((stage, index) => {
                  const done = index < completed
                  return (
                    <li key={stage} className="flex items-center gap-3 text-sm">
                      {done ? (
                        <CircleCheck className="h-5 w-5 text-success" aria-hidden="true" />
                      ) : (
                        <Clock className="h-5 w-5 text-muted" aria-hidden="true" />
                      )}
                      <span className={done ? 'font-medium text-fg' : 'text-muted'}>{stage}</span>
                      <span className="ml-auto rounded-full bg-brand-soft px-2 py-0.5 text-xs font-semibold text-brand">
                        {done ? 'Done' : 'Coming soon'}
                      </span>
                    </li>
                  )
                })}
              </ol>
            </GlassCard>
          </motion.div>
        )}
      </div>
    </PageShell>
  )
}
