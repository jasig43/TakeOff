import { useCallback, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ChevronLeft, ChevronRight, RefreshCw, SearchX } from 'lucide-react'
import { reviewApi } from '../api/applicationApi'
import type { ApplicationStatus } from '../api/types'
import { AppShell } from '../components/layout/AppShell'
import { Button } from '../components/ui/Button'
import { GlassCard } from '../components/ui/GlassCard'
import { SelectField } from '../components/ui/SelectField'
import { Skeleton } from '../components/ui/Skeleton'
import { StatusBadge } from '../components/ui/StatusBadge'
import { TextField } from '../components/ui/TextField'
import { useApiResource } from '../hooks/useApiResource'
import { useDebounce } from '../hooks/useDebounce'
import { usePageTitle } from '../hooks/usePageTitle'
import { STATUS_LABEL, formatDateTime } from '../utils/formatting'

const PAGE_SIZE = 10
const FILTERS: ApplicationStatus[] = ['PENDING_REVIEW', 'APPROVED', 'REJECTED']

function parseStatus(value: string | null): ApplicationStatus | undefined {
  return FILTERS.find((status) => status === value)
}

export default function AdminApplicationsPage() {
  usePageTitle('Applications')
  const [params, setParams] = useSearchParams()
  const status = parseStatus(params.get('status'))
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0)

  const [text, setText] = useState('')
  const query = useDebounce(text.trim())

  const fetchPage = useCallback(() => reviewApi.list({ status, q: query, page, size: PAGE_SIZE }), [status, query, page])
  const { data, error, loading, reload } = useApiResource(fetchPage, 'We could not load the applications.')

  const update = (changes: { status?: string; page?: number }) => {
    const next = new URLSearchParams(params)
    if ('status' in changes) {
      if (changes.status) next.set('status', changes.status)
      else next.delete('status')
    }
    if (changes.page) next.set('page', String(changes.page))
    else next.delete('page')
    setParams(next, { replace: true })
  }

  const rows = data?.items ?? []
  const totalPages = data?.totalPages ?? 0

  return (
    <AppShell>
      <div className="mx-auto max-w-6xl space-y-6">
        <header>
          <h1 className="font-display text-3xl font-bold">Driver applications</h1>
          <p className="mt-2 text-muted">Search, filter and open any submitted application to review it.</p>
        </header>

        <GlassCard className="p-5">
          <form role="search" onSubmit={(e) => e.preventDefault()} className="grid gap-4 sm:grid-cols-[1fr_16rem]">
            <TextField
              label="Search"
              type="search"
              placeholder="Name, email, phone, reference or plate"
              value={text}
              onChange={(e) => {
                setText(e.target.value)
                update({ page: 0 })
              }}
              autoComplete="off"
            />
            <SelectField
              label="Status"
              value={status ?? ''}
              onChange={(e) => update({ status: e.target.value, page: 0 })}
            >
              <option value="">All submitted</option>
              {FILTERS.map((value) => (
                <option key={value} value={value}>
                  {STATUS_LABEL[value]}
                </option>
              ))}
            </SelectField>
          </form>
        </GlassCard>

        {error && (
          <GlassCard className="space-y-4 p-6" role="alert">
            <p className="font-medium text-danger">{error}</p>
            <Button variant="secondary" onClick={reload}>
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Try again
            </Button>
          </GlassCard>
        )}

        {!error && loading && !data && (
          <GlassCard className="space-y-4 p-6" aria-busy="true" aria-label="Loading applications">
            {[0, 1, 2, 3].map((n) => (
              <Skeleton key={n} className="h-10 w-full" />
            ))}
          </GlassCard>
        )}

        {!error && data && rows.length === 0 && (
          <GlassCard className="flex flex-col items-center gap-3 p-10 text-center">
            <SearchX className="h-8 w-8 text-muted" aria-hidden="true" />
            <p className="font-medium">No applications found</p>
            <p className="text-sm text-muted">
              {status || query ? 'Try a different search or filter.' : 'Submitted applications will appear here.'}
            </p>
          </GlassCard>
        )}

        {!error && data && rows.length > 0 && (
          <GlassCard className="overflow-hidden" aria-busy={loading}>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[46rem] text-left text-sm">
                <caption className="sr-only">Submitted driver applications</caption>
                <thead className="border-b border-line text-muted">
                  <tr>
                    <th scope="col" className="px-5 py-3 font-medium">
                      Reference
                    </th>
                    <th scope="col" className="px-5 py-3 font-medium">
                      Driver
                    </th>
                    <th scope="col" className="px-5 py-3 font-medium">
                      Vehicle
                    </th>
                    <th scope="col" className="px-5 py-3 font-medium">
                      Submitted
                    </th>
                    <th scope="col" className="px-5 py-3 font-medium">
                      Status
                    </th>
                    <th scope="col" className="px-5 py-3 font-medium">
                      <span className="sr-only">Action</span>
                    </th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-line">
                  {rows.map((row) => (
                    <tr key={row.id} className="align-middle">
                      <td className="px-5 py-3 font-mono font-semibold">{row.referenceId ?? '-'}</td>
                      <td className="px-5 py-3">
                        <p className="font-medium">{row.driverName}</p>
                        <p className="text-muted">{row.driverEmail}</p>
                      </td>
                      <td className="px-5 py-3">{row.plateNumber ?? '-'}</td>
                      <td className="px-5 py-3">{formatDateTime(row.submittedAt)}</td>
                      <td className="px-5 py-3">
                        <StatusBadge status={row.status} />
                      </td>
                      <td className="px-5 py-3 text-right">
                        <Link
                          to={`/admin/applications/${row.id}`}
                          className="font-semibold text-brand underline-offset-4 hover:underline"
                          aria-label={`Review application ${row.referenceId ?? row.id} from ${row.driverName}`}
                        >
                          Review
                        </Link>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-5 py-3">
              <p className="text-sm text-muted" role="status">
                {data.totalItems} {data.totalItems === 1 ? 'application' : 'applications'}, page {data.page + 1} of{' '}
                {Math.max(totalPages, 1)}
              </p>
              <div className="flex gap-2">
                <Button variant="secondary" onClick={() => update({ page: page - 1 })} disabled={page <= 0}>
                  <ChevronLeft className="h-4 w-4" aria-hidden="true" />
                  Previous
                </Button>
                <Button variant="secondary" onClick={() => update({ page: page + 1 })} disabled={page + 1 >= totalPages}>
                  Next
                  <ChevronRight className="h-4 w-4" aria-hidden="true" />
                </Button>
              </div>
            </div>
          </GlassCard>
        )}
      </div>
    </AppShell>
  )
}
