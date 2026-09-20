import { motion } from 'framer-motion'
import { CircleCheck, CircleX, Clock, RefreshCw, Users, type LucideIcon } from 'lucide-react'
import { Link } from 'react-router-dom'
import { reviewApi } from '../api/applicationApi'
import { AppShell } from '../components/layout/AppShell'
import { Button, ButtonLink } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Skeleton } from '../components/ui/Skeleton'
import { StatusBadge } from '../components/ui/StatusBadge'
import { useApiResource } from '../hooks/useApiResource'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'
import { formatDateTime } from '../utils/formatting'

interface Tile {
  label: string
  value: number
  icon: LucideIcon
  tone: string
  to: string
}

// Module-level so the fetcher's identity is stable between renders (see useApiResource).
const fetchQueue = () => reviewApi.list({ status: 'PENDING_REVIEW', page: 0, size: 5 })

export default function AdminDashboardPage() {
  usePageTitle('Admin dashboard')
  const { user } = useAuth()
  const summary = useApiResource(reviewApi.summary, 'We could not load the application counts.')
  const queue = useApiResource(fetchQueue, 'We could not load the review queue.')

  const loading = summary.loading && !summary.data
  const error = summary.error || queue.error
  const retry = () => {
    summary.reload()
    queue.reload()
  }

  const tiles: Tile[] = summary.data
    ? [
        { label: 'Pending review', value: summary.data.pendingReview, icon: Clock, tone: 'text-accent', to: '/admin/applications?status=PENDING_REVIEW' },
        { label: 'Approved', value: summary.data.approved, icon: CircleCheck, tone: 'text-success', to: '/admin/applications?status=APPROVED' },
        { label: 'Not approved', value: summary.data.rejected, icon: CircleX, tone: 'text-danger', to: '/admin/applications?status=REJECTED' },
        { label: 'Total submitted', value: summary.data.totalSubmitted, icon: Users, tone: 'text-brand', to: '/admin/applications' },
      ]
    : []

  return (
    <AppShell>
      <div className="mx-auto max-w-5xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">Admin dashboard</h1>
          <p className="mt-2 text-muted">Signed in as {user?.email}. Review driver applications and record decisions.</p>
        </header>

        {loading && (
          <div aria-busy="true" aria-label="Loading dashboard" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {[0, 1, 2, 3].map((n) => (
              <GlassCard key={n} className="space-y-3 p-5">
                <Skeleton className="h-5 w-1/2" />
                <Skeleton className="h-8 w-1/3" />
              </GlassCard>
            ))}
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

        {!loading && !summary.error && summary.data && (
          <motion.ul
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            aria-label="Application counts"
            className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4"
          >
            {tiles.map(({ label, value, icon: Icon, tone, to }) => (
              <li key={label}>
                <Link to={to} className="glass block rounded-3xl p-5 transition hover:bg-brand-soft" aria-label={`${label}: ${value}`}>
                  <Icon className={`h-6 w-6 ${tone}`} aria-hidden="true" />
                  <p className="mt-3 text-sm text-muted">{label}</p>
                  <p className="font-display text-3xl font-bold">{value}</p>
                </Link>
              </li>
            ))}
          </motion.ul>
        )}

        {!summary.error && summary.data && (
          <GlassCard className="p-6">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <h2 className="text-lg font-semibold">Waiting for review</h2>
              <ButtonLink to="/admin/applications" variant="secondary">
                All applications
              </ButtonLink>
            </div>

            {queue.loading && !queue.data && (
              <div className="mt-4 space-y-3" aria-label="Loading review queue">
                <Skeleton className="h-10 w-full" />
                <Skeleton className="h-10 w-full" />
              </div>
            )}

            {queue.data && queue.data.items.length === 0 && (
              <p className="mt-4 text-sm text-muted">Nothing is waiting. New submissions will show up here.</p>
            )}

            {queue.data && queue.data.items.length > 0 && (
              <ul className="mt-4 divide-y divide-line">
                {queue.data.items.map((row) => (
                  <li key={row.id} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                    <div className="min-w-0">
                      <p className="font-medium">{row.driverName}</p>
                      <p className="text-sm text-muted">
                        <span className="font-mono">{row.referenceId ?? '-'}</span>, submitted {formatDateTime(row.submittedAt)}
                      </p>
                    </div>
                    <div className="flex items-center gap-3">
                      <StatusBadge status={row.status} />
                      <Link
                        to={`/admin/applications/${row.id}`}
                        className="font-semibold text-brand underline-offset-4 hover:underline"
                        aria-label={`Review application ${row.referenceId ?? row.id} from ${row.driverName}`}
                      >
                        Review
                      </Link>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </GlassCard>
        )}
      </div>
    </AppShell>
  )
}
