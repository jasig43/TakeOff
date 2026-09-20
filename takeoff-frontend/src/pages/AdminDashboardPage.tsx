import { motion } from 'framer-motion'
import { ClipboardList, RefreshCw, ShieldCheck, Users } from 'lucide-react'
import { adminApi } from '../api/authApi'
import { PageShell } from '../components/layout/PageShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { Skeleton } from '../components/ui/Skeleton'
import { useApiResource } from '../hooks/useApiResource'
import { useAuth } from '../hooks/useAuth'
import { usePageTitle } from '../hooks/usePageTitle'

export default function AdminDashboardPage() {
  usePageTitle('Admin dashboard')
  const { user } = useAuth()
  const { data: health, error, loading, reload } = useApiResource(adminApi.getHealth, 'We could not reach the admin service.')

  return (
    <PageShell>
      <div className="mx-auto max-w-4xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">Admin dashboard</h1>
          <p className="mt-2 text-muted">Signed in as {user?.email}. Driver review tools arrive in a later phase.</p>
        </header>

        <GlassCard className="p-6" aria-busy={loading}>
          <h2 className="flex items-center gap-2 text-lg font-semibold">
            <ShieldCheck className="h-5 w-5 text-success" aria-hidden="true" />
            Role-based access check
          </h2>
          {loading && (
            <div className="mt-4 space-y-3" aria-label="Checking access">
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-5 w-1/2" />
            </div>
          )}
          {!loading && error && (
            <div role="alert" className="mt-4 space-y-3">
              <p className="font-medium text-danger">{error}</p>
              <Button variant="secondary" onClick={reload}>
                <RefreshCw className="h-4 w-4" aria-hidden="true" />
                Try again
              </Button>
            </div>
          )}
          {!loading && health && (
            <motion.dl initial={{ opacity: 0 }} animate={{ opacity: 1 }} className="mt-4 grid gap-3 text-sm sm:grid-cols-3">
              <div>
                <dt className="text-muted">Backend status</dt>
                <dd className="font-semibold text-success">{health.status}</dd>
              </div>
              <div>
                <dt className="text-muted">Verified role</dt>
                <dd className="font-semibold">{health.role}</dd>
              </div>
              <div>
                <dt className="text-muted">Server time (UTC)</dt>
                <dd className="font-semibold">{new Date(health.timestamp).toISOString().replace('T', ' ').slice(0, 19)}</dd>
              </div>
              <p className="text-muted sm:col-span-3">{health.message}</p>
            </motion.dl>
          )}
        </GlassCard>

        <div className="grid gap-4 md:grid-cols-2">
          <GlassCard className="p-6">
            <Users className="h-6 w-6 text-brand" aria-hidden="true" />
            <h2 className="mt-3 text-lg font-semibold">Driver applications</h2>
            <p className="mt-1 text-sm text-muted">Browse and filter submitted applications.</p>
            <span className="mt-3 inline-block rounded-full bg-accent-soft px-2.5 py-0.5 text-xs font-semibold text-accent">
              Coming soon
            </span>
          </GlassCard>
          <GlassCard className="p-6">
            <ClipboardList className="h-6 w-6 text-brand" aria-hidden="true" />
            <h2 className="mt-3 text-lg font-semibold">Review queue</h2>
            <p className="mt-1 text-sm text-muted">Approve or reject applications and notify drivers.</p>
            <span className="mt-3 inline-block rounded-full bg-accent-soft px-2.5 py-0.5 text-xs font-semibold text-accent">
              Coming soon
            </span>
          </GlassCard>
        </div>
      </div>
    </PageShell>
  )
}
